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

import org.semanticweb.owlapi.io.* 
import org.semanticweb.owlapi.model.*
import org.semanticweb.owlapi.util.*
import org.semanticweb.owlapi.apibinding.*
import org.semanticweb.owlapi.reasoner.*

import org.semanticweb.elk.owlapi.*
import org.semanticweb.elk.reasoner.config.*

def o1Location = new File(args[0])
def o2Location = new File(args[1])
def newName = args[2]
def outFile = args[3]

def manager = OWLManager.createOWLOntologyManager()
def config = new OWLOntologyLoaderConfiguration()
config.setFollowRedirects(true)

def o1 = manager.loadOntologyFromOntologyDocument(new IRIDocumentSource(IRI.create(o1Location.toURI())), config)
def o2 = manager.loadOntologyFromOntologyDocument(new IRIDocumentSource(IRI.create(o2Location.toURI())), config)

def newOntologyID = IRI.create("http://reality.rehab/ontologies/"+newName)
def newOntology = new OWLOntologyMerger(manager).createMergedOntology(manager, newOntologyID)

manager.saveOntology(newOntology, IRI.create(new File(outFile).toURI()))
