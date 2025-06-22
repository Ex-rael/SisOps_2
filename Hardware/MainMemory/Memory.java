package Hardware.MainMemory;

import Hardware.CPU.CPU.Opcode;

public class Memory {

    public static class Word {
        public Opcode opc;
        public int r1;
        public int r2;
        public int p;
        public boolean valid = false;  // Indica se a palavra é válida (página residente)
        public boolean modified = false; // Flag de modificação (para write-back)
        public int referenceCounter = 0; // Contador de referências (para algoritmos de substituição)
        public long lastAccessTime;

        public Word(Opcode _opc, int _r1, int _r2, int _p) {
            opc = _opc;
            r1 = _r1;
            r2 = _r2;
            p = _p;
        }
    }

    public int pageSize;
    public Boolean[] frames;
    public int tamMem;
    public Word[] m;
    public int[] pageTable;  // Tabela de páginas global auxiliar

    public Memory(int size, int pageSize) {
        tamMem = size;
        m = new Word[tamMem];
        putWordsInMemory();

        this.pageSize = pageSize;
        createFrames(pageSize);

        // Inicializa a tabela de páginas auxiliar
        this.pageTable = new int[tamMem / pageSize];
        for (int i = 0; i < pageTable.length; i++) {
            pageTable[i] = -1;  // -1 indica página não mapeada
        }
    }

    private void createFrames(int pageSize) {
        frames = new Boolean[tamMem / pageSize];
        for (int i = 0; i < tamMem / pageSize; i++) {
            frames[i] = true;  // true indica frame livre
        }
    }

    private void putWordsInMemory() {
        for (int i = 0; i < tamMem; i++) {
            m[i] = new Word(Opcode.___, -1, -1, -1);
        }
    }

    // Método para marcar uma página como válida
    public void validatePage(int frame) {
        int start = frame * pageSize;
        int end = start + pageSize;
        for (int i = start; i < end; i++) {
            m[i].valid = true;
        }
    }

    // Método para invalidar uma página
    public void invalidatePage(int frame) {
        int start = frame * pageSize;
        int end = start + pageSize;
        for (int i = start; i < end; i++) {
            m[i].valid = false;
        }
    }

    // Método para verificar se uma página está na memória
    public boolean isPageValid(int frame) {
        if (frame < 0 || frame >= frames.length) return false;
        return m[frame * pageSize].valid;
    }

    // Método para obter o frame de uma página
    public int getFrameForPage(int page) {
        if (page < 0 || page >= pageTable.length) return -1;
        return pageTable[page];
    }

    // Método para mapear página para frame
    public void mapPageToFrame(int page, int frame) {
        if (page >= 0 && page < pageTable.length && frame >= 0 && frame < frames.length) {
            pageTable[page] = frame;
            validatePage(frame);
        }
    }

    public void markPageModified(int frame) {
        int start = frame * pageSize;
        for (int i = start; i < start + pageSize; i++) {
            m[i].modified = true;
        }
    }

    public boolean isPageModified(int frame) {
        return m[frame * pageSize].modified;
    }
}