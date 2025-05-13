package net.tokenu.mail;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import commons.FileUtil;
import commons.json.JsonUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Host {
    public static void main(String[] args) throws Exception {
        merge();
    }

    private static void merge() throws Exception {
        // Read hosts.json file
        String content = FileUtil.readString("hosts.json");

        // Parse JSON
        JsonObject jsonObject = JsonParser.parseString(content).getAsJsonObject();
        JsonArray domains = jsonObject.getAsJsonArray("domains");

        Map<String,JsonObject> first = new HashMap<>();
        for (JsonElement element : new ArrayList<>(domains.asList())) {
            JsonObject domainObj = element.getAsJsonObject();
            JsonElement patternElement = domainObj.get("pattern");
            String host = domainObj.get("host").getAsString();

            if (!first.containsKey(host)) {
                first.put(host, domainObj);
            }
            else {
                domains.remove(element);
                JsonObject firstObj = first.get(host);
                JsonElement firstPatternElement = firstObj.get("pattern");

                // Check if pattern is a string or an array
                if (firstPatternElement.isJsonArray()) {
                    // Handle array of patterns
                    JsonArray patterns = firstPatternElement.getAsJsonArray();
                    patterns.addAll(gerPatternArray(patternElement));
                }
                else {
                    //String pattern = patternElement.getAsString();

                    // Create a new JsonArray and add the pattern
                    JsonArray patterns = new JsonArray();
                    patterns.add(firstPatternElement.getAsString());
                    patterns.addAll(gerPatternArray(patternElement));

                    // Replace the string pattern with the array
                    //firstObj.remove("pattern");
                    firstObj.add("pattern", patterns);
                }
            }
        }

        FileUtil.write(JsonUtil.prettyPrinting(jsonObject), "hosts.json");
    }

    private static JsonArray gerPatternArray(JsonElement patternElement) {
        JsonArray patternArray = new JsonArray();
        // Check if pattern is a string or an array
        if (patternElement.isJsonArray()) {
            // Handle array of patterns
            JsonArray patterns = patternElement.getAsJsonArray();
            for (JsonElement patternItem : patterns) {
                String pattern = patternItem.getAsString();
                patternArray.add(pattern);
            }
        } else {
            // Handle single pattern (string)
            String pattern = patternElement.getAsString();
            patternArray.add(pattern);
        }
        return patternArray;
    }
}
