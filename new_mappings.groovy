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
def bestMatches = [:]
new File('morbid.txt').text.split('\n').each { line ->
  line.tokenize('\t').eachWithIndex { code, idx ->
    if(idx == 0) { return; }
    code = code.toLowerCase()
    code = code.replaceAll('"', '')
    morbids[code] = []
  }
}

new File('align.tsv').text.split('\n').each { line ->
  def field = line.tokenize('\t')
  println field[1]
  if(morbids.containsKey(field[1])) {
    morbids[field[1]] << field[2]
  } else {
    def shortCode = field[1].tokenize('.')
    if(shortCode && morbids.containsKey(shortCode[0])) {
      morbids[shortCode[0]] << field[2] 
    }

    morbids.each { k, v ->
      if(k.indexOf(field[1]) == 0 && !morbids[k].contains(field[2])) {
        morbids[k] << field[2]
      }
    }
  }
}

def out = morbids.collect { k, v -> if(v.size() > 0) { k + '\t' + v.join(',') } }
out.removeAll([null])
new File('newmappings.tsv').text = out.join('\n')
