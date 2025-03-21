import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.nio.file.Files
import java.nio.file.Path

if (args.length != 1) {
    println("Usage: jb-env-postman <input-file>")
    return
}

def iff = new File(args[0])

def jbEnv = new JsonSlurper().parse(iff) as Map<String, String>
def ks = jbEnv.keySet()

ks.each { k ->
    if (k.toString().toLowerCase() === "class") {
        return
    }
    def env = [:]
    env["name"] = k
    env["values"] = []
    env["_postman_variable_scope"] = "environment"
    def v = jbEnv[k] as Map<String, String>
    v.keySet().each {
        val_it -> {
            def val = [:]
            val["key"] = val_it
            val["value"] = v[val_it]
            val["enabled"] = true
            env["values"].add(val)
        }
    }
    def off = new File("${iff.parent}/output/${k}.env.json")
    Files.createDirectories(Path.of(off.parent))
    off.delete()
    off.createNewFile()
    off << JsonOutput.toJson(env)
}
