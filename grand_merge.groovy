@Grapes([
  @Grab(group='net.sourceforge.owlapi', module='owlapi-api', version='4.1.0'),
  @Grab(group='net.sourceforge.owlapi', module='owlapi-apibinding', version='4.1.0'),
  @Grab(group='net.sourceforge.owlapi', module='owlapi-impl', version='4.1.0'),
  @Grab(group='net.sourceforge.owlapi', module='owlapi-parsers', version='4.1.0'),
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

@Field def eConf = ReasonerConfiguration.getConfiguration()
eConf.setParameter(ReasonerConfiguration.NUM_OF_WORKING_THREADS, "24")
eConf.setParameter(ReasonerConfiguration.INCREMENTAL_MODE_ALLOWED, "true")
eConf.setParameter(ReasonerConfiguration.INCREMENTAL_TAXONOMY, "true")
@Field def reasonerFactory = new ElkReasonerFactory();
@Field def rConf = new ElkReasonerConfiguration(ElkReasonerConfiguration.getDefaultOwlReasonerConfiguration(new NullReasonerProgressMonitor()), eConf);

// Load PhenomeNET
def PNET_ORIGINAL = 'phenomenet.owl'
def pnet = manager.loadOntologyFromOntologyDocument(new IRIDocumentSource(IRI.create(new File(PNET_ORIGINAL).toURI())), config)

def idCounter = 30000
def IRI_PREFIX = "http://aber-owl.net/phenotype.owl#PHENO:"

// Add top level 'disease' class
def dClass = factory.getOWLClass(IRI.create(IRI_PREFIX + idCounter))
idCounter++
def dAxiom = factory.getOWLDeclarationAxiom(newClass)
manager.applyChange(new AddAxiom(pnet, dAxiom)

// organise omim
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

// For each OMIM, add a new disease class, with has_phenotype for each
