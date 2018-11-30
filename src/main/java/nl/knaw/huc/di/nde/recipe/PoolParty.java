package nl.knaw.huc.di.nde.recipe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmItem;
import nl.knaw.huc.di.nde.TermDTO;
import nl.mpi.tla.util.Saxon;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.Rio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class PoolParty implements RecipeInterface {

  private static final Logger LOG = LoggerFactory.getLogger(PoolParty.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Override
  public List<TermDTO> fetchMatchingTerms(XdmItem config, String match) {

    ArrayList<TermDTO> terms = Lists.newArrayList();
    try {
      String api = Saxon.xpath2string(config, "nde:api", null, OpenSKOS.NAMESPACES);
      String query = Saxon.xpath2string(config, "nde:query", null, OpenSKOS.NAMESPACES);

      URLEncoder.encode(query, "UTF-8");
      query = URLDecoder.decode(query.replace("${match}", match).trim(), "UTF-8");

      CloseableHttpClient client = HttpClients.createDefault();
      HttpPost post = new HttpPost(api);
      ArrayList<BasicNameValuePair> parameters = Lists.newArrayList(
        new BasicNameValuePair("query", query));
      post.setEntity(new UrlEncodedFormEntity(parameters));
      // HttpGet httpGet = new HttpGet(api.toString());
      CloseableHttpResponse httpResponse = client.execute(post);
      // String theString = entityToString(httpResponse.getEntity());
      // LOG.info("response {}", theString);

      Model model = Rio.parse(httpResponse.getEntity().getContent(), "https://data.cultureelerfgoed.nl/term/id/cht/",
        RDFFormat.RDFXML);
      String subject = null;
      TermDTO term = null;
      for (Statement statement : model) {
        if(subject == null || !statement.getSubject().stringValue().equals(subject)) {
          subject = statement.getSubject().stringValue();
          if(term != null) {
            terms.add(term);
          }
          term = new TermDTO();
          term.uri = new URI(subject);
        }
        if(statement.getPredicate().getLocalName().equals("prefLabel")) {
          term.prefLabel.add(statement.getObject().stringValue());
        }
        if(statement.getPredicate().getLocalName().equals("altLabel")) {
          term.altLabel.add(statement.getObject().stringValue());
        }
        if(statement.getPredicate().getLocalName().equals("hiddenLabel")) {
          term.definition.add(statement.getObject().stringValue());
        }
        if(statement.getPredicate().getLocalName().equals("scopeNote")) {
          term.scopeNote.add(statement.getObject().stringValue());
        }

      }
      terms.add(term);


      // JsonNode jsonNode = OBJECT_MAPPER.readTree(theString);
      // ArrayNode results = (ArrayNode) jsonNode.get("results").get("bindings");
      // results.iterator().forEachRemaining(res -> {
      //   TermDTO term = new TermDTO();
      //   String uri = res.get("uri").get("value").asText();
      //   try {
      //     term.uri = new URI(uri);
      //     term.definition = getOptional(res, "hiddenLabel");
      //     term.altLabel = getOptional(res, "altLabel");
      //     term.prefLabel = Lists.newArrayList(res.get("prefLabel").get("value").textValue());
      //     term.scopeNote = getOptional(res, "scopeNote");
      //   } catch (URISyntaxException e) {
      //     LOG.error("Uri {} not an URI", uri);
      //   }
      //
      //   terms.add(term);
      // });

    } catch (IOException | SaxonApiException | URISyntaxException e) {
      LOG.error("Request failed: ", e);
    }


    return terms;
  }

  private ArrayList<String> getOptional(JsonNode res, String field) {
    if (res.has(field)) {
      return Lists.newArrayList(res.get(field).get("value").asText());
    }
    return Lists.newArrayList();
  }

  private String entityToString(HttpEntity responseEntity) throws IOException {
    StringWriter writer = new StringWriter();
    IOUtils.copy(responseEntity.getContent(), writer, "UTF-8");
    return writer.toString();
  }
}
