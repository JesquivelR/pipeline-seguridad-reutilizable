package seguridad

// Perfil por lenguaje: reglas de analisis y archivo de dependencias que
// se espera en el proyecto. Equivale al paso "Elegir las reglas segun el
// lenguaje" del workflow de GitHub Actions.
class Lenguajes implements Serializable {

    private static final Map PERFILES = [
        java     : [reglas: 'p/java p/owasp-top-ten',                manifiesto: 'pom.xml'],
        node     : [reglas: 'p/javascript p/nodejs p/owasp-top-ten', manifiesto: 'package-lock.json'],
        python   : [reglas: 'p/python p/owasp-top-ten',              manifiesto: 'requirements.txt'],
        go       : [reglas: 'p/golang p/owasp-top-ten',              manifiesto: 'go.mod'],
        terraform: [reglas: 'p/terraform',                           manifiesto: '']
    ]

    static Map perfil(String lenguaje) {
        return PERFILES.get(lenguaje, [reglas: 'p/default', manifiesto: ''])
    }
}
