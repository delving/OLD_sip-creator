package eu.delving.groovy;

import eu.delving.metadata.CodeOut;
import eu.delving.metadata.MappingFunction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class StandardMappingFunctions {

    private static List<String> mappingFunctionsScript = getMappingFunctionsScript();
    private static List<MappingFunction> mappingFunctions = getMappingFunctionsFromScript(mappingFunctionsScript);

    private StandardMappingFunctions() {
    }

    private static List<String> getMappingFunctionsScript() {
        List<String> script = new ArrayList<>();
        InputStream in = StandardMappingFunctions.class.getResourceAsStream("/functions.groovy");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                script.add(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return script;
    }

    private static List<MappingFunction> getMappingFunctionsFromScript(List<String> script) {
        List<MappingFunction> mappingFunctions = new ArrayList<>();
        for(String line : script) {
            if (line.contains("#def")) {
                String name = line.split("\\s+", 2)[1].split("\\(")[0].trim();
                String[] typedParameters = line.split("\\(")[1].split("\\)")[0].split(",");
                List<String> parameterNames = new ArrayList<>();
                for(String typedParam : typedParameters) {
                    parameterNames.add(typedParam.trim().split("\\s+")[1]);
                }

                mappingFunctions.add(MappingFunction.createStandardMappingFunction(name, parameterNames));
            }
        }
        return mappingFunctions;
    }

    public static void appendStandardFunctionsToScript(CodeOut codeOut) {
        for (String line : mappingFunctionsScript) {
            codeOut.line(line);
        }
    }

    public static List<MappingFunction> asList() {
        return mappingFunctions;
    }
}