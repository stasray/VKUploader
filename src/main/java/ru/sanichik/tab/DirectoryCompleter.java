package ru.sanichik.tab;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import ru.sanichik.core.Main;
import ru.sanichik.managers.FileSystemManager;

import java.util.List;
import java.util.Set;

/**
 * TODO: Не работает в Docker, надо заменить
 * */
public class DirectoryCompleter implements Completer {
    private final FileSystemManager fsm;

    public DirectoryCompleter(FileSystemManager fsm) {
        this.fsm = fsm;
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        String currentDir = Main.currDirectory;
        Set<String> folders = fsm.getFolders(currentDir);

        System.out.println("Completer called! Current dir: " + currentDir);
        System.out.println("Available folders: " + folders);

        for (String folder : folders) {
            candidates.add(new Candidate(folder));
        }
    }
}
