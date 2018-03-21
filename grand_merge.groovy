@Grapes([
  @Grab(group='net.sourceforge.owlapi', module='owlapi-api', version='5.1.4'),
  @Grab(group='net.sourceforge.owlapi', module='owlapi-apibinding', version='5.1.4'),
  @Grab(group='net.sourceforge.owlapi', module='owlapi-impl', version='5.1.4'),
  @Grab(group='net.sourceforge.owlapi', module='owlapi-parsers', version='5.1.4'),
  @Grab(group='org.slf4j', module='slf4j-log4j12', version='1.7.10'),
  @Grab('com.xlson.groovycsv:groovycsv:1.1'),
  @GrabConfig(systemClassLoader=true)
])

import org.semanticweb.owlapi.io.* 
import org.semanticweb.owlapi.model.*
import org.semanticweb.owlapi.util.*
import org.semanticweb.owlapi.apibinding.*
import groovy.transform.Field
import com.xlson.groovycsv.CsvParser

// 1: Organise OMIM
def omim = [:]
new File('phenotype_annotation.tab').split('\n').each {
  def fields = it.split('\t')
  def omimId = fields[4]
  def hpId = fields[3]

  if(!omim.containsKey(omimId)) {
    omim[omimId] = [
      'id': omimId,
      'dName' fields[2],
      'phenotypes': []
    ]
  }

  omim[omimId].phenotypes << hpId
}

// 2: OWLAPI config

@Field def eConf = ReasonerConfiguration.getConfiguration()
eConf.setParameter(ReasonerConfiguration.NUM_OF_WORKING_THREADS, "24")
eConf.setParameter(ReasonerConfiguration.INCREMENTAL_MODE_ALLOWED, "true")
eConf.setParameter(ReasonerConfiguration.INCREMENTAL_TAXONOMY, "true")
@Field def reasonerFactory = new ElkReasonerFactory();
@Field def rConf = new ElkReasonerConfiguration(ElkReasonerConfiguration.getDefaultOwlReasonerConfiguration(new NullReasonerProgressMonitor()), eConf);

def manager = OWLManager.createOWLOntologyManager()
def config = new OWLOntologyLoaderConfiguration()
config.setFollowRedirects(true)

// 3: Load PhenomeNET
def PNET_ORIGINAL = 'phenomenet.owl'
def pnet = manager.loadOntologyFromOntologyDocument(new IRIDocumentSource(IRI.create(new File(PNET_ORIGINAL).toURI())), config)
def oFactory = manager.getOWLDataFactory()

def idCounter = 30000
def IRI_PREFIX = "http://aber-owl.net/phenotype.owl#PHENO:"

// Classify PhenomeNET
/*def oReasoner = reasonerFactory.createReasoner(pnet, rConf)
oReasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY)*/

// 4: Get children of DOID diseases

def es = new EntitySearcher()
def doidDiseases = oFactory.getOWLClass(IRI.create("http://purl.obolibrary.org/obo/DOID_4"));
println es.getSubclasses(pnet, doidDiseases)
