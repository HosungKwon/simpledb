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
    private int id;
    // a hack to remember the last page that had a free slot
    private volatile int lastEmptyPage = -1;
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
        id = f.getAbsoluteFile().hashCode();
    }

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
        return id;
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
        try {
            // some code goes here
            RandomAccessFile readFile = new RandomAccessFile(dataFile,"r");
            int offset = BufferPool.PAGE_SIZE * pid.pageNumber();
            byte[] data = new byte[BufferPool.PAGE_SIZE];
            readFile.seek(offset);
            readFile.read(data,0,BufferPool.getPageSize());
            readFile.close();
            return new HeapPage((HeapPageId)pid,data);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(HeapFile.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(HeapFile.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    // see DbFile.java for javadocs
    public void writePage(Page page) throws IOException {
        // some code goes here
        // not necessary for lab1
    }

    /**
     * Returns the number of pages in this HeapFile.
     */
    public int numPages() {
        // some code goes here
        return (int) (dataFile.length()/BufferPool.PAGE_SIZE);
    }

    // see DbFile.java for javadocs
    public ArrayList<Page> insertTuple(TransactionId tid, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
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
                    i, id);
            HeapPageId pid = new HeapPageId(id, i);
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
                        i, id);

                // we mistakenly got here through lastEmptyPage, just add a page
                // XXX we know this isn't very pretty.
                if (lastEmptyPage != -1) {
                    lastEmptyPage = -1;
                    break;
                }
                continue;
            }
            Debug.log(4, "HeapFile.addTuple: %d free slots in table %d",
                    p.getNumEmptySlots(), id);
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
                .getPage(tid, new HeapPageId(id, numPages() - 1),
                        Permissions.READ_WRITE);
        p.insertTuple(t);
        lastEmptyPage = p.getId().pageNumber();
        // System.out.println("nfetches = " + nfetches);
        dirtypages.add(p);
        return dirtypages;
    }

    // see DbFile.java for javadocs
    public ArrayList<Page> deleteTuple(TransactionId tid, Tuple t) throws DbException,
            TransactionAbortedException {
        HeapPage p = (HeapPage) Database.getBufferPool().getPage(
                tid,
                new HeapPageId(id, t.getRecordId().getPageId()
                        .pageNumber()), Permissions.READ_WRITE);
        p.deleteTuple(t);
        ArrayList<Page> pages = new ArrayList<Page>();
        pages.add(p);
        return pages;
    }

    // see DbFile.java for javadocs
    public DbFileIterator iterator(TransactionId tid) {
        // some code goes here
        return new HeapFileIterator(tid);
    }
    
    public class HeapFileIterator implements DbFileIterator{
        
        private TransactionId trid;
        private Iterator<Tuple> tuples;
        private Page currentPage = null;
        private PageId currentPageId = null;
        private int currentPageNo = 0;
        
        public HeapFileIterator(TransactionId tid){
            trid = tid;
        }


        @Override
        public void open() throws DbException, TransactionAbortedException {
            currentPageId = new HeapPageId(HeapFile.this.getId(), currentPageNo);
            currentPage = Database.getBufferPool().getPage(trid, currentPageId, null);
            tuples = ((HeapPage) currentPage).iterator();
        }

        @Override
        public boolean hasNext() throws DbException, TransactionAbortedException {
            if (tuples != null) {
                if (tuples.hasNext()) {
                    return true;
                }
                else {
                    if (currentPageNo < HeapFile.this.numPages()-1) {
                        int tableId = HeapFile.this.getId();
                        currentPageNo += 1;
                        currentPageId = new HeapPageId(tableId, currentPageNo);
                        currentPage = Database.getBufferPool().getPage(trid, currentPageId, null);
                        tuples = ((HeapPage) currentPage).iterator();
                        return hasNext();
                    }
                }
            }
	return false;
        }

        @Override
        public Tuple next() throws DbException, TransactionAbortedException, NoSuchElementException {
            if (tuples!=null&&tuples.hasNext()) {
                return tuples.next();
            }
            else {
                throw new NoSuchElementException();
            }
        }

        @Override
        public void rewind() throws DbException, TransactionAbortedException {
            int tableId = HeapFile.this.getId();
            currentPageNo = 0;
            currentPageId = new HeapPageId(tableId, currentPageNo);
            currentPage = Database.getBufferPool().getPage(trid, currentPageId, null);
            tuples = ((HeapPage) currentPage).iterator();            
        }

        @Override
        public void close() {
            currentPageNo = 0;
            currentPageId = null;
            currentPage = null;
            tuples = null;
        }
        
    }

}

