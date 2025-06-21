package Hardware.Disk;

import Hardware.MainMemory.Memory.Word;

public class Disk {
    private Word[][] diskPages;
    private boolean[] allocatedDiskPages;

    public Disk(int diskSize) {
        diskPages = new Word[diskSize][];
        allocatedDiskPages = new boolean[diskSize];
    }

    public int allocatePage() {
        for (int i = 0; i < allocatedDiskPages.length; i++) {
            if (!allocatedDiskPages[i]) {
                allocatedDiskPages[i] = true;
                return i;
            }
        }
        return -1; // Disco cheio
    }

    public void deallocatePage(int diskPage) {
        allocatedDiskPages[diskPage] = false;
        diskPages[diskPage] = null;
    }

    public void savePage(int diskPage, Word[] pageData) {
        diskPages[diskPage] = pageData;
    }

    public Word[] loadPage(int diskPage) {
        return diskPages[diskPage];
    }
}