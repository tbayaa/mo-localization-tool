import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.apache.commons.text.CaseUtils
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.nio.charset.Charset

const val dartInputFilesDirectoryPath = "files/inputs"
const val nonAgencyEnJsonPath = "files/outputs/jsons/en.json"
const val nonAgencyArJsonPath = "files/outputs/jsons/ar.json"
const val outputDartDirectory = "files/outputs/dart/"
const val jsonSectionName = "insuranceNetworkGeneralSearch"

fun main() {
    val dartFiles = File(dartInputFilesDirectoryPath).walkBottomUp().filter { !it.isDirectory }.toList()

    val fileToLinesMap = HashMap<File, FileLines>()

    dartFiles.forEach { dartFile ->
        dartFile.useLines {
            fileToLinesMap[dartFile] = FileLines(originalLines = it.toList())
        }
    }

    val hardcodedStringRegex = Regex("(?<!import )'(.*?)'")
    val onlyLettersRegex = Regex("[^A-Za-z ]")

    val localizationMap: LinkedHashMap<String, String> = LinkedHashMap()
    var duplicateKeyIndex = 1

    fileToLinesMap.forEach { (file, fileLines) ->
        fileLines.originalLines.forEachIndexed { index, currentLine ->
            val modifiedLine = currentLine.replace(hardcodedStringRegex) { matchResult ->
                val initialJsonKeyCandidate = matchResult.groupValues[1]
                val jsonKeyCandidateWithNoCustomChars = onlyLettersRegex.replace(initialJsonKeyCandidate, "")
                var jsonKey = CaseUtils.toCamelCase(jsonKeyCandidateWithNoCustomChars, false)

                if (jsonKey.isNotBlank() && !initialJsonKeyCandidate.contains('_') && !initialJsonKeyCandidate.contains(
                        "res/assets"
                    )
                ) {
                    if (!localizationMap.containsValue(matchResult.groupValues[1])) {
                        if (localizationMap.containsKey(jsonKey)) {
                            jsonKey += duplicateKeyIndex++
                        }
                    }

                    localizationMap[jsonKey] = matchResult.groupValues[1]

                    "localeKeys.${jsonKey}.tr()"
                } else {
                    matchResult.value
                }
            }
            fileLines.modifiedLines.add(index, modifiedLine)
        }

        val indexOfClassDeclaration = fileLines.modifiedLines.indexOfFirst {
            it.contains("class")
        }

        fileLines.modifiedLines.add(
            indexOfClassDeclaration,
            "\n" + "import 'package:benefits/src/translations/locale_keys.g.dart';\n" + "import 'package:benefits/src/core/utils/localization_utils.dart';\n" + "final localeKeys = LocaleKeys.app.home.healthServices.${jsonSectionName};"
        )

        val relativePathOfInput = file.path.substringAfter(dartInputFilesDirectoryPath)
        val destinationPath = outputDartDirectory + relativePathOfInput

        createFileAndFoldersIfNotExist(destinationPath).printWriter().use { out ->
            fileLines.modifiedLines.forEach {
                out.println(it)
            }
        }
    }

    val subSection = JsonObject()
    val rootJson = JsonObject().also {
        it.add(jsonSectionName, subSection)
    }

    localizationMap.forEach { (key, value) ->
        subSection.addProperty(key, value)
    }

    createFileAndFoldersIfNotExist(nonAgencyEnJsonPath)
    writeJsonContentToFile(rootJson, nonAgencyEnJsonPath)

    createFileAndFoldersIfNotExist(nonAgencyArJsonPath)
    writeJsonContentToFile(JsonObject(), nonAgencyArJsonPath)

}

private fun writeJsonContentToFile(rootJson: JsonObject, jsonPath: String) {
    PrintWriter(FileWriter(jsonPath, Charset.defaultCharset())).use {
            //no need to gson, actually. Just for making it pretty
            val gson = GsonBuilder().setPrettyPrinting().create()
            val parseString = JsonParser.parseString(rootJson.toString())
            it.write(gson.toJson(parseString))
        }
}

private fun createFileAndFoldersIfNotExist(path: String): File {
    return File(path).also {
        it.parentFile.mkdirs()
        it.createNewFile()
    }
}

data class FileLines(val originalLines: List<String>, val modifiedLines: ArrayList<String> = arrayListOf())