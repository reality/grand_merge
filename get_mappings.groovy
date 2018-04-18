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
import com.xlson.groovycsv.CsvParser

import org.semanticweb.owlapi.io.* 
import org.semanticweb.owlapi.model.*
import org.semanticweb.owlapi.model.parameters.*
import org.semanticweb.owlapi.util.*
import org.semanticweb.owlapi.apibinding.*
import org.semanticweb.owlapi.search.*
import org.semanticweb.owlapi.reasoner.*
import org.semanticweb.elk.owlapi.*
import org.semanticweb.elk.reasoner.config.*

// thing file
def morbids = [:]
new File('morbid.txt').text.split('\n').each { line ->
  line.tokenize('\t').eachWithIndex { code, idx ->
    if(idx == 0) { return; }
    code = code.replaceAll('"', '')
    morbids[code] = []
  }
}

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

def O_FILE = 'doid.owl' // change to hp.owl for hp obvs
def ont = manager.loadOntologyFromOntologyDocument(new IRIDocumentSource(IRI.create(new File(O_FILE).toURI())), config)

// Classify Ontology
println 'Reasoning'

def oReasoner = reasonerFactory.createReasoner(ont, rConf)
oReasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY)

def diseaseClass = df.getOWLClass(IRI.create("http://purl.obolibrary.org/obo/DOID_4"))
//def diseaseClass = df.getOWLClass(IRI.create("http://purl.obolibrary.org/obo/HP_0000001"))

oReasoner.getSubClasses(diseaseClass, false).each { cNode ->
  cNode.each { dClass ->
    println dClass.getIRI()
    ont.annotationAssertionAxioms(dClass.getIRI(), Imports.INCLUDED).each { assAxiom ->
      def ann = assAxiom.getAnnotation()
      def key = ann.getProperty().getIRI().getFragment()
      def value = ann.getValue().asLiteral()

      if(!value.isPresent()) {
        return;
      }
      value = value.get().getLiteral()

      if(key == 'hasDbXref') {
        def (db, code) = value.tokenize(':')
        if(!code) { return; } //why
        def shortCode = code.tokenize('.')[0]
        if(morbids.containsKey(code) && !morbids[code].contains(dClass.getIRI().getFragment())) {
          morbids[code] << dClass.getIRI().getFragment()
          println 'found 1'
        }
        if(morbids.containsKey(shortCode) && !morbids[shortCode].contains(dClass.getIRI().getFragment())) {
          morbids[shortCode] << dClass.getIRI().getFragment()
          println 'found 1'
        }
      }
    }
  }
}
def out = morbids.collect { k, v -> if(v.size() > 0) { k + '\t' + v.join(',') } }
out.removeAll([null])
new File('mappings.tsv').text = out.join('\n')
