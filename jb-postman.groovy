import groovy.io.FileType
import groovy.json.JsonOutput
import java.nio.file.Files
import java.nio.file.Path

File input_folder
File output_folder
if (args.length == 1) {
    input_folder = new File(args[0])
    output_folder = new File(args[0])
} else if (args.length == 2) {
    input_folder = new File(args[0])
    output_folder = Files.createDirectories(Path.of(args[1])).toFile()
} else {
    println("Usage: jb-postman <input-folder> [<output-folder>]")
    return
}

if (!input_folder.exists()) {
    println("Input folder does not exist")
    return
}

input_folder.eachFileRecurse(FileType.FILES) { iff ->
    def items = []
    def item = [:]
    def req = [:]
    def headers = []
    def body = [:]
    def url = [:]
    def query = []
    def data = ""
    def reqName = ""
    item.put("request", req)
    req.put("body", body)
    req.put("header", headers) // not a typo
    req.put("url", url)
    url.put("query", query)
    int count = 0
    def startedJson = false

    if (!iff.name.endsWith(".http")) return
    def info = ["name": "${iff.name.replace(".", "-")}",
                "schema" : "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"]
    iff.eachLine {
        def l = it.strip()
        switch (l) {
        // assumption - each commented line starts with a single hashtag followed by a space
            case { l.startsWith("# ")}:
                break

                // assumption - each request starts with a line with the triple hashtags and eventually the title
            case { l.startsWith("###") }:
                reqName = l.replace("#", "").trim().replace(" ", "_")
                items.add(item)
                item = [:]
                l.strip()
                req = [:]
                headers = []
                body = [:]
                url = [:]
                query = []
                data = ""
                item.put("request", req)
                req.put("body", body)
                req.put("header", headers) // not a typo
                req.put("url", url)
                url.put("query", query)
                break

            case { l ==~ "(GET|PUT|POST|DELETE|OPTIONS).+" }:
                item.put("name", "request_${++count}_${reqName}")
                req.put("method", l.split(" ")[0].trim())
                def rawUrl = l.split(" ")[1]
                url.put("raw", rawUrl)
                if (rawUrl.contains("://")) {
                    def extUrl = rawUrl.split("://")
                    url.put("protocol", extUrl[0])
                    def cleanUrl = extUrl[1].split("\\?")[0] // remove query part
                    if (cleanUrl.contains("/")) { // if has path part
                        url.put("host", cleanUrl.split("/")?[0]?.split("\\."))
                        url.put("path", cleanUrl.split("/")?[1..-1])
                    } else {
                        url.put("host", cleanUrl.split("\\."))
                    }
                } else {
                    url.put("host", rawUrl.split("/")?[0]?.split("\\."))
                    url.put("path", rawUrl.split("/")?[1..-1])
                }
                // assumption - there should be no url encoding going here since jb does it for you
                if (!rawUrl.contains("?")) break
                rawUrl.split("\\?")?[1].split("&")?.each {
                    def spq = it.split("=")
                    query.add(["key": spq[0].trim(), "value": spq[1].trim()])
                }
                break

            case { l.contains(":") && !startedJson }:
                def sph = l.split(":")
                def header = ["key": sph[0].trim(), "value": sph[1].trim(), "type": "text"]
                headers.add(header)
                break

            case { l.startsWith("{") }:
                if (l.endsWith("}")) {
                    req.put("data", l)
                    startedJson = false
                }
                body.put("mode", "raw")
                body.put("options", ["raw": ["language": "json"]])
                startedJson = true
                data += "$l\n"
                break

            case { l.contains(":") && startedJson }:
                data += l
                break

            case { l.endsWith("}") && startedJson }:
                data += l
                if (data) {
                    body.put("raw", data)
                }
                startedJson = false
                break
        }
        return
    }

    items.add(item)

    items.removeIf {it -> it["request"]["header"].isEmpty()}

    def collection = ["info": info, "items": items]
    def off = new File(output_folder, "${iff.name}.json")
    off.delete()
    off.createNewFile()
    off << JsonOutput.toJson(collection)
}