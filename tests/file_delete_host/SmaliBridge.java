// SPDX-License-Identifier: MIT
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import brut.androlib.src.SmaliBuilder;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;
import com.android.tools.smali.dexlib2.writer.builder.DexBuilder;
import com.android.tools.smali.dexlib2.writer.io.FileDataStore;
import com.android.tools.smali.baksmali.Baksmali;
import com.android.tools.smali.baksmali.BaksmaliOptions;
/** Uses the existing apktool library API, whose command-line main is removed. */
public final class SmaliBridge {
    public static void main(String[] args) throws Exception {
        Opcodes opcodes = new Opcodes(30, -1);
        if ("assemble".equals(args[0])) {
            File source = new File(args[1]);
            SmaliBuilder assembler = new SmaliBuilder(source, 30);
            DexBuilder dex = new DexBuilder(opcodes);
            try (Stream<Path> paths = Files.walk(source.toPath())) {
                paths.filter(p -> p.toString().endsWith(".smali")).sorted().forEach(p ->
                    assembler.buildFile(source.toPath().relativize(p).toString(), dex));
            }
            FileDataStore store = new FileDataStore(new File(args[2]));
            dex.writeTo(store);
            store.raf.close();
        } else if ("disassemble".equals(args[0])) {
            MultiDexContainer container = DexFileFactory.loadDexContainer(new File(args[1]), opcodes);
            if (container.getDexEntryNames().size() != 1) throw new IllegalArgumentException("Expected one DEX");
            String name = (String) container.getDexEntryNames().get(0);
            BaksmaliOptions options = new BaksmaliOptions();
            options.localsDirective = true;
            Baksmali.disassembleDexFile(container.getEntry(name).getDexFile(), new File(args[2]), 1, options);
        } else throw new IllegalArgumentException("Unknown operation");
    }
}
