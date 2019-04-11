package nl.knaw.huc.di.nde.recipe;

import com.google.common.collect.Lists;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmItem;
import nl.knaw.huc.di.nde.TermDTO;
import nl.mpi.tla.util.Saxon;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;
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
import nl.knaw.huc.di.nde.Registry;

public class SparqlEndpoint implements RecipeInterface {

  private static final Logger LOG = LoggerFactory.getLogger(SparqlEndpoint.class);

  @Override
  public List<TermDTO> fetchMatchingTerms(XdmItem config, String match) {

    // Optional debugging settings for serious problems... 
    //System.setProperty("org.apache.commons.logging.Log","org.apache.commons.logging.impl.SimpleLog");
    //System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
    //System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.wire", "DEBUG");

    ArrayList<TermDTO> terms = Lists.newArrayList();
    try {
      String api = Saxon.xpath2string(config, "nde:api", null, Registry.NAMESPACES);
      String query = Saxon.xpath2string(config, "nde:query", null, Registry.NAMESPACES);
      String base = Saxon.xpath2string(config, "nde:base", null, Registry.NAMESPACES);

      // see if api supports the use of '*'; should be boolean instead of string
      String wildcard = Saxon.xpath2string(config, "nde:wildcard",null, Registry.NAMESPACES);

      System.err.println("DBG: wildcard support "+wildcard);

      URLEncoder.encode(query, "UTF-8");

      // remove '*' if wildcards are not supported
      if ( wildcard.equals("no") ) {
         match = match.replaceAll("\\*","");
      }

      query = URLDecoder.decode(query.replace("${match}", match).trim(), "UTF-8");

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

      Model model = Rio.parse(httpResponse.getEntity().getContent(), base, RDFFormat.RDFXML);
      String subject = null;
      TermDTO term = null;
      for (Statement statement : model) {
        if (subject == null || !statement.getSubject().stringValue().equals(subject)) {
          subject = statement.getSubject().stringValue();
          if (term != null) {
            terms.add(term);
          }
          term = new TermDTO();
          term.uri = new URI(subject);
        }
        if (statement.getPredicate().getLocalName().equals("prefLabel")) {
          term.prefLabel.add(statement.getObject().stringValue());
        }
        if (statement.getPredicate().getLocalName().equals("altLabel")) {
          term.altLabel.add(statement.getObject().stringValue());
        }
        if (statement.getPredicate().getLocalName().equals("hiddenLabel")) {
          term.definition.add(statement.getObject().stringValue());
        }
        if (statement.getPredicate().getLocalName().equals("scopeNote")) {
          term.scopeNote.add(statement.getObject().stringValue());
        }
        if (statement.getPredicate().getLocalName().equals("broader")) {
          term.broader.add(statement.getObject().stringValue());
        }
        if (statement.getPredicate().getLocalName().equals("narrower")) {
          term.narrower.add(statement.getObject().stringValue());
        }
      }
      terms.add(term);

    } catch (IOException | SaxonApiException | URISyntaxException e) {
      LOG.error("Request failed: ", e);
    }

    return terms;
  }
}
