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

// 1: Organise OMIM
def omim = [:]
new File('phenotype_annotation.tab').text.split('\n').each {
  def fields = it.split('\t')
  def omimId = fields[5]
  def hpId = fields[4]

  if(!omim.containsKey(omimId)) {
    omim[omimId] = [
      'id': omimId,
      'dName': fields[2],
      'phenotypes': [],
      'done': false
    ]
  }

  omim[omimId].phenotypes << hpId
}

println omim.keySet()

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

// 3: Load PhenomeNET

println 'Loading' 
def PNET_ORIGINAL = 'phenomenet.owl'
def pnet = manager.loadOntologyFromOntologyDocument(new IRIDocumentSource(IRI.create(new File(PNET_ORIGINAL).toURI())), config)

def idCounter = 30000
def IRI_PREFIX = "http://aber-owl.net/phenotype.owl#PHENO:"

// Classify PhenomeNET
println 'Reasoning'

def oReasoner = reasonerFactory.createReasoner(pnet, rConf)
oReasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY)

// 4: Get children of DOID diseases

// some useful individuals ;)
def diseaseClass = df.getOWLClass(IRI.create("http://purl.obolibrary.org/obo/DOID_4"))
def hasSymptom = df.getOWLObjectProperty("http://purl.obolibrary.org/obo/doid#has_symptom")

def diseasesAdded = []
def phenotypesAdded = 0

oReasoner.getSubClasses(diseaseClass, false).each { cNode ->
  cNode.each { dClass ->
    println dClass.getIRI()

    // Find DB references. Should really be using EntitySearcher, but https://github.com/owlcs/owlapi/issues/745
    pnet.annotationAssertionAxioms(dClass.getIRI(), Imports.INCLUDED).each { assAxiom ->
      def ann = assAxiom.getAnnotation()
      def key = ann.getProperty().getIRI().getFragment()
      def value = ann.getValue().asLiteral()

      if(!value.isPresent()) {
        return;
      }
      value = value.get().getLiteral()

      if(key == 'hasDbXref') {
        println 'Found ' + value
        if(omim.containsKey(value)) {
          println 'Adding disease phenotypes!!'
          omim[value].done = true

          omim[value].phenotypes.each { hpId ->
            def hpIRI = IRI.create("http://purl.obolibrary.org/obo/HP_"+hpId.split(':')[1])
            def hpClass = df.getOWLClass(hpIRI)

            def symptomRestriction = df.getOWLObjectSomeValuesFrom(hasSymptom, hpClass)
            def newAssertion = df.getOWLSubClassOfAxiom(dClass, symptomRestriction)

            manager.applyChange(new AddAxiom(pnet, newAssertion))

            phenotypesAdded++
          }

          if(!diseasesAdded.contains(dClass.getIRI())) {
            diseasesAdded << dClass.getIRI()
          }
        }
      }
    }
  }
}

File newPnetFile = new File("new_phenomenet.owl")
manager.saveOntology(pnet, IRI.create(newPnetFile.toURI()))

println "Got phenotype information for ${diseasesAdded.size()} diseases in DOID, adding a total of ${phenotypesAdded} phenotypes."
println "OMIM coverage: " + omim.collect { k, v -> v.done }.size() + '/' + omim.size()
