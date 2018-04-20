@Grapes([
  @Grab(group='net.sourceforge.owlapi', module='owlapi-api', version='5.1.4'),
  @Grab(group='net.sourceforge.owlapi', module='owlapi-apibinding', version='5.1.4'),
  @Grab(group='net.sourceforge.owlapi', module='owlapi-impl', version='5.1.4'),
  @Grab(group='net.sourceforge.owlapi', module='owlapi-parsers', version='5.1.4'),
  @Grab(group='org.apache.commons', module='commons-rdf-api', version='0.5.0'),
  @Grab(group='org.slf4j', module='slf4j-log4j12', version='1.7.10'),
  @Grab('com.xlson.groovycsv:groovycsv:1.1'),

  @GrabResolver(name='sonatype-nexus-snapshots', root='https://oss.sonatype.org/content/repositories/snapshots/'),
  @Grab(group='org.semanticweb.elk', module='elk-owlapi5', version='0.5.0-SNAPSHOT'),

  @GrabConfig(systemClassLoader=true)
])

import groovy.transform.Field

import org.semanticweb.owlapi.io.* 
import org.semanticweb.owlapi.model.*
import org.semanticweb.owlapi.model.parameters.*
import org.semanticweb.owlapi.util.*
import org.semanticweb.owlapi.apibinding.*
import org.semanticweb.owlapi.search.*
import org.semanticweb.owlapi.reasoner.*
import org.semanticweb.elk.owlapi.*
import org.semanticweb.elk.reasoner.config.*

// 2: OWLAPI config

@Field def eConf = ReasonerConfiguration.getConfiguration()
eConf.setParameter(ReasonerConfiguration.NUM_OF_WORKING_THREADS, "24")
eConf.setParameter(ReasonerConfiguration.INCREMENTAL_MODE_ALLOWED, "true")
@Field def reasonerFactory = new ElkReasonerFactory()
@Field def rConf = new ElkReasonerConfiguration(ElkReasonerConfiguration.getDefaultOwlReasonerConfiguration(new NullReasonerProgressMonitor()), eConf)

def df = OWLManager.getOWLDataFactory()
def manager = OWLManager.createOWLOntologyManager()
def config = new OWLOntologyLoaderConfiguration()
config.setFollowRedirects(true)
config.setLoadAnnotationAxioms(true)

// 3: Load ontology and mappings

println 'Loading' 
def ontFile = args[0]
def mapFile = args[1]
def outFile = args[2]
def newMapFile = args[3]

// load & reverse map
def map = [:]
new File(mapFile).text.split('\n').each { line ->
  def (icd, doids) = line.tokenize('\t')
  doids.tokenize(',').each { doid ->
    if(!map.containsKey(doid)) {
      map[doid] = []
    }
    map[doid] << icd.toUpperCase()
  }
}
def newDotMap = [:]

def ontology = manager.loadOntologyFromOntologyDocument(new IRIDocumentSource(IRI.create(new File(ontFile).toURI())), config)

// Classify (probably unnecessary but oReasoner interface is much less annoying to query)
println 'Reasoning'

def oReasoner = reasonerFactory.createReasoner(ontology, rConf)
oReasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY)

// 4: Get children of DOID diseases
def diseaseClass = df.getOWLClass(IRI.create("http://purl.obolibrary.org/obo/DOID_4"))
def count = 0

// add equiv axioms
oReasoner.getSubClasses(diseaseClass, false).each { cNode ->
  cNode.each { dClass ->
    println dClass.getIRI()

    if(map.containsKey(dClass.getIRI().toString())) {
      map[dClass.getIRI().toString()].each { icd ->
        def PREFIX = "http://purl.bioontology.org/ontology/ICD10CM/"

        def actualICDClass = ([IRI.create(PREFIX + icd)] + (1..icd.size()-1).collect { // stupid stupid stupid stupid
          potentialClass = IRI.create(PREFIX + icd.substring(0, it) + '.' + icd.substring(it))
        }).find {
          ontology.containsClassInSignature(it)
        }

        if(actualICDClass) {
          def newAxiom = df.getOWLEquivalentClassesAxiom(dClass, df.getOWLClass(actualICDClass))
          manager.applyChange(new AddAxiom(ontology, newAxiom))
          count++

          if(!newDotMap.containsKey(icd)) {
            newDotMap[icd] = actualICDClass.toString()
          }
        }
      }
    }
  }
}

// save ontology
File newFile = new File(outFile)
manager.saveOntology(ontology, IRI.create(newFile.toURI()))

new File(newMapFile).text = newDotMap.collect { k, v -> k + '\t' + v }.join('\n')

println "Added ${count} new axioms of equivalence"
