package nl.knaw.huc.di.nde.recipe;

import com.google.common.collect.Lists;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmItem;
import nl.knaw.huc.di.nde.TermDTO;
import nl.knaw.huc.di.nde.RefDTO;
import nl.mpi.tla.util.Saxon;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Paths;
import nl.knaw.huc.di.nde.Registry;

public class SparqlEndpoint implements RecipeInterface {

  private static final Logger LOG = LoggerFactory.getLogger(SparqlEndpoint.class);

  // function to proces Sparql results that contain a 
  // label, uri or both, the result is stored in a RefDTO object
  private static RefDTO processItem(Model m, Statement st) {
    RefDTO ref = new RefDTO();
    Value value = st.getObject();
    if (value instanceof Literal) {
      ref.url="";
      ref.label = st.getObject().stringValue();
    }
    else {
      ref.url = st.getObject().stringValue();
      ref.label = "";
      for (Statement stRef: m.filter((IRI)st.getObject(), null, null)) {
        if (stRef.getPredicate().getLocalName().equals("prefLabel")){
          ref.label = stRef.getObject().stringValue();
        }
      }
    }
    return ref;
  }

  @Override
  public List<TermDTO> fetchMatchingTerms(XdmItem config, String match) {

    // Optional debugging settings for serious problems... 
    //System.setProperty("org.apache.commons.logging.Log","org.apache.commons.logging.impl.SimpleLog");
    //System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
    //System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.wire", "DEBUG");

    ArrayList<TermDTO> terms = Lists.newArrayList();
    try {
      String queries_path = "/app/nde-termennetwerk/conf/queries/";
      String api = Saxon.xpath2string(config, "nde:api", null, Registry.NAMESPACES);
      String rq = Saxon.xpath2string(config, "nde:query", null, Registry.NAMESPACES);
      String base = Saxon.xpath2string(config, "nde:base", null, Registry.NAMESPACES);

      // see if api supports the use of '*'; should be boolean instead of string
      String wildcard = Saxon.xpath2string(config, "nde:wildcard",null, Registry.NAMESPACES);

      //System.err.println("DBG: wildcard support "+wildcard);

      String query = "";
      try
      {
          query = new String ( Files.readAllBytes( Paths.get( queries_path + rq )) );
      }
      catch (IOException e) { e.printStackTrace(); }

      //URLEncoder.encode(query, "UTF-8");

      // remove '*' if wildcards are not supported
      if ( wildcard.equals("no") ) {
         match = match.replaceAll("\\*","");
      }

      //query = URLDecoder.decode(query.replace("_SEARCH_", match).trim(), "UTF-8");
      query = query.replace("_SEARCH_", match).trim();

      // print out the query that is executed on the sparql endpoint
      System.err.println("DBG: query "+query);

      CloseableHttpClient client = HttpClients.createDefault();
      HttpPost post = new HttpPost(api);

      // force sparql endpoint to talk RDF/XML 
      post.setHeader("Accept", "application/rdf+xml");
      
      ArrayList<BasicNameValuePair> parameters = Lists.newArrayList(
        new BasicNameValuePair("query", query));
      post.setEntity(new UrlEncodedFormEntity(parameters));
      CloseableHttpResponse httpResponse = client.execute(post);

      // see examples in https://rdf4j.eclipse.org/documentation/getting-started/ 
      ValueFactory vf = SimpleValueFactory.getInstance();
      IRI SkosConcept = vf.createIRI("http://www.w3.org/2004/02/skos/core#Concept");

      Model model = Rio.parse(httpResponse.getEntity().getContent(), base, RDFFormat.RDFXML);

      System.err.println("Start parsing of the received RDF, number of triples is: "+model.size());
      
      for (Statement stConcepts : model.filter(null,null,SkosConcept)) {
        String subject = stConcepts.getSubject().stringValue();
        System.err.println("Processing query result, working on "+subject);
        TermDTO term = new TermDTO();
        term.uri = new URI(subject);

        for (Statement statement : model.filter((IRI)stConcepts.getSubject(), null, null)) {

          if (statement.getPredicate().getLocalName().equals("prefLabel")) {
            term.prefLabel.add(statement.getObject().stringValue());
          }

          if (statement.getPredicate().getLocalName().equals("altLabel")) {
            term.altLabel.add(statement.getObject().stringValue());
          }

          if (statement.getPredicate().getLocalName().equals("hiddenLabel")) {
            term.hiddenLabel.add(statement.getObject().stringValue());
          }

          if (statement.getPredicate().getLocalName().equals("definition")) {
            term.definition.add(statement.getObject().stringValue());
          }

          if (statement.getPredicate().getLocalName().equals("scopeNote")) {
            term.scopeNote.add(statement.getObject().stringValue());
          }
          // broader, narrower and related can contain a label a url or both
          // and will be stored in a RefDTO object, the processItem routine 
          // takes care of storing the available values in the right way 
          if (statement.getPredicate().getLocalName().equals("broader")) {
            term.broader.add(processItem(model,statement));
          }

          if (statement.getPredicate().getLocalName().equals("related")) {
            term.related.add(processItem(model,statement));
          }

          if (statement.getPredicate().getLocalName().equals("narrower")) {
            term.narrower.add(processItem(model,statement));
          } 
        }
        terms.add(term);
      }
    } catch (IOException | SaxonApiException | URISyntaxException e) {
      LOG.error("Request failed: ", e);
    }

    return terms;
  }
}
