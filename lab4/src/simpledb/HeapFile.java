package simpledb;

import java.io.*;
import java.util.*;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HeapFile is an implementation of a DbFile that stores a collection of tuples
 * in no particular order. Tuples are stored on pages, each of which is a fixed
 * size, and the file is simply a collection of those pages. HeapFile works
 * closely with HeapPage. The format of HeapPages is described in the HeapPage
 * constructor.
 * 
 * @see simpledb.HeapPage#HeapPage
 * @author Sam Madden
 */
public class HeapFile implements DbFile {
    
    private File dataFile;
    private TupleDesc tDesc;
    private int MAX;
    private int tableid;
    /**
     * Constructs a heap file backed by the specified file.
     * 
     * @param f
     *            the file that stores the on-disk backing store for this heap
     *            file.
     */
    public HeapFile(File f, TupleDesc td) {
        // some code goes here
        dataFile = f;
        tDesc = td;
        MAX = (int) f.length() / BufferPool.getPageSize() - 1;
        tableid = dataFile.getAbsoluteFile().hashCode();
    }

    // a hack to remember the last page that had a free slot
    private volatile int lastEmptyPage = -1;

    /**
     * Returns the File backing this HeapFile on disk.
     * 
     * @return the File backing this HeapFile on disk.
     */
    public File getFile() {
        // some code goes here
        return dataFile;
    }

    /**
     * Returns an ID uniquely identifying this HeapFile. Implementation note:
     * you will need to generate this tableid somewhere ensure that each
     * HeapFile has a "unique id," and that you always return the same value for
     * a particular HeapFile. We suggest hashing the absolute file name of the
     * file underlying the heapfile, i.e. f.getAbsoluteFile().hashCode().
     * 
     * @return an ID uniquely identifying this HeapFile.
     */
    public int getId() {
        // some code goes here
        return tableid;
    }

    /**
     * Returns the TupleDesc of the table stored in this DbFile.
     * 
     * @return TupleDesc of this DbFile.
     */
    public TupleDesc getTupleDesc() {
        // some code goes here
        return tDesc;
    }

    // see DbFile.java for javadocs
    public Page readPage(PageId pid) {
        // some code goes here
        int pageNo = pid.pageNumber();
        int o = BufferPool.getPageSize() * pageNo;
        byte[] data = new byte[BufferPool.getPageSize()];
        RandomAccessFile RAF = null;
        try {
            RAF = new RandomAccessFile(dataFile, "r");
            // Move the file pointer to the offset we calculated.
            RAF.seek((long) o); 
            RAF.read(data, 0, BufferPool.getPageSize());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();	
        }
        try {
            return new HeapPage((HeapPageId) pid, data);
	} catch (IOException e) {
            e.printStackTrace();
            return null;
	} finally {
            try {
		RAF.close();
            } catch (IOException e) {
		e.printStackTrace();
            }
        }
    }

    // see DbFile.java for javadocs
    public void writePage(Page page) throws IOException {
        // some code goes here
        // not necessary for lab1
        HeapPage p = (HeapPage) page;
        // System.out.println("Writing back page " + p.getId().pageno());
        byte[] data = p.getPageData();
        RandomAccessFile rf = new RandomAccessFile(dataFile, "rw");
        rf.seek(p.getId().pageNumber() * BufferPool.getPageSize());
        rf.write(data);
        rf.close();
    }

    /**
     * Returns the number of pages in this HeapFile.
     */
    public int numPages() {
        // some code goes here
        return (int) (dataFile.length()/BufferPool.getPageSize());
    }

    // see DbFile.java for javadocs
    public ArrayList<Page> insertTuple(TransactionId tid, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
        // some code goes here
        ArrayList<Page> dirtypages = new ArrayList<Page>();

        // find the first page with a free slot in it
        int i = 0;
        if (lastEmptyPage != -1)
            i = lastEmptyPage;
        // XXX: Would it not be better to scan from numPages() to 0 since the
        // last pages are more likely to have empty slots?
        for (; i < numPages(); i++) {
            Debug.log(
                    4,
                    "HeapFile.addTuple: checking free slots on page %d of table %d",
                    i, tableid);
            HeapPageId pid = new HeapPageId(tableid, i);
            HeapPage p = (HeapPage) Database.getBufferPool().getPage(tid, pid,
                    Permissions.READ_WRITE);

            // no empty slots
            //
            // think about why we have to invoke releasePage here.
            // can you think of ways where
            if (p.getNumEmptySlots() == 0) {
                Debug.log(
                        4,
                        "HeapFile.addTuple: no free slots on page %d of table %d",
                        i, tableid);

                // we mistakenly got here through lastEmptyPage, just add a page
                // XXX we know this isn't very pretty.
                if (lastEmptyPage != -1) {
                    lastEmptyPage = -1;
                    break;
                }
                continue;
            }
            Debug.log(4, "HeapFile.addTuple: %d free slots in table %d",
                   p.getNumEmptySlots(), tableid);
            p.insertTuple(t);
            lastEmptyPage = p.getId().pageNumber();
            // System.out.println("nfetches = " + nfetches);
            dirtypages.add(p);
            return dirtypages;
        }

        // no empty slots -- append a page
        // This must be synchronized so that the append operation is atomic.
        // Otherwise a second
        // thread could be blocked just after opening the file. The first
        // transaction flushes
        // new tuples to the page. The second transaction then overwrites the
        // data with an empty
        // page, losing the new data.
        synchronized (this) {
            BufferedOutputStream bw = new BufferedOutputStream(
                    new FileOutputStream(dataFile, true));
            byte[] emptyData = HeapPage.createEmptyPageData();
            bw.write(emptyData);
            bw.close();
        }

        // by virtue of writing these bits to the HeapFile, it is now visible.
        // so some other dude may have obtained a read lock on the empty page
        // we just created---which is ok, we haven't yet added the tuple.
        // we just need to lock the page before we can add the tuple to it.

        HeapPage p = (HeapPage) Database.getBufferPool()
                .getPage(tid, new HeapPageId(tableid, numPages() - 1),
                        Permissions.READ_WRITE);
        p.insertTuple(t);
        lastEmptyPage = p.getId().pageNumber();
        // System.out.println("nfetches = " + nfetches);
        dirtypages.add(p);
        return dirtypages;
        // not necessary for lab1
    }

    // see DbFile.java for javadocs
    public ArrayList<Page> deleteTuple(TransactionId tid, Tuple t) throws DbException,
            TransactionAbortedException {
        // some code goes here
        HeapPage p = (HeapPage) Database.getBufferPool().getPage(
                tid,
                new HeapPageId(tableid, t.getRecordId().getPageId()
                        .pageNumber()), Permissions.READ_WRITE);
        p.deleteTuple(t);
        ArrayList<Page> pages = new ArrayList<Page>();
        pages.add(p);
        return pages;
        // not necessary for lab1
    }

    // see DbFile.java for javadocs
    public DbFileIterator iterator(TransactionId tid) {
        // some code goes here
        
        return new HeapFileIterator(tid); 
    }
    
    public class HeapFileIterator implements DbFileIterator {
        
        private TransactionId trid;
        private int currPageNo = 0;
        private Page currPage = null;
        private PageId currPageId = null;
        private Iterator<Tuple> tuples = null;
        private TransactionId tid = null;
        
        public HeapFileIterator(TransactionId tid) {
            trid = tid;
        }
        
        @Override
        public void open() throws DbException, TransactionAbortedException {
            int tableId = HeapFile.this.getId();
            currPageId = new HeapPageId(HeapFile.this.getId(), currPageNo);
            currPage = Database.getBufferPool().getPage(tid, currPageId, null);
            tuples = ((HeapPage) currPage).iterator();
        }

        @Override
        public boolean hasNext() throws DbException, TransactionAbortedException {
            if (tuples != null) {
		if (tuples.hasNext()) {
                    return true;
		} else {
                    if (currPageNo < HeapFile.this.numPages()-1) {
			int tableId = HeapFile.this.getId();
			currPageNo++;
                        currPageId = new HeapPageId(tableId, currPageNo);
			currPage = Database.getBufferPool().getPage(tid, currPageId, null);
			tuples = ((HeapPage) currPage).iterator();
			return this.hasNext();
                    }
		}
            }
	return false;
        }

        @Override
        public Tuple next() throws DbException, TransactionAbortedException, NoSuchElementException {
            if (tuples != null) {
		return tuples.next();
            } else {
                throw new NoSuchElementException();
            }
        }

        @Override
        public void rewind() throws DbException, TransactionAbortedException {
            int tableId = HeapFile.this.getId();
            currPageNo = 0;
            currPageId = new HeapPageId(tableId, currPageNo);
            currPage = Database.getBufferPool().getPage(tid, currPageId, null);
            tuples = ((HeapPage) currPage).iterator();
        }

        @Override
        public void close() {
            currPageNo = 0;
            currPageId = null;
            currPage = null;
            tuples = null;
        }
        
    }
}

