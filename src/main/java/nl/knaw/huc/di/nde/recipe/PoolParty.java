package nl.knaw.huc.di.nde.recipe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Lists;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmItem;
import nl.knaw.huc.di.nde.TermDTO;
import nl.mpi.tla.util.Saxon;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class PoolParty implements RecipeInterface {

  public static final Logger LOG = LoggerFactory.getLogger(PoolParty.class);
  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Override
  public List<TermDTO> fetchMatchingTerms(XdmItem config, String match) {

    ArrayList<TermDTO> terms = Lists.newArrayList();
    try {
      StringBuilder api = new StringBuilder(Saxon.xpath2string(config, "nde:api", null, OpenSKOS.NAMESPACES));
      String query = Saxon.xpath2string(config, "nde:query", null, OpenSKOS.NAMESPACES);

      URLEncoder.encode(query, "UTF-8");
      api.append("?format=application/json&query=").append(query.replace("${match}", match).trim());

      CloseableHttpClient client = HttpClients.createDefault();
      HttpGet httpGet = new HttpGet(api.toString());
      CloseableHttpResponse httpResponse = client.execute(httpGet);
      String theString = entityToString(httpResponse.getEntity());
      // LOG.info("response {}", theString);
      JsonNode jsonNode = OBJECT_MAPPER.readTree(theString);
      LOG.info("json results: {}", jsonNode.get("results"));
      LOG.info("json bindings: {}", jsonNode.get("results").get("bindings"));
      ArrayNode results = (ArrayNode) jsonNode.get("results").get("bindings");
      results.iterator().forEachRemaining(res -> {
        TermDTO term = new TermDTO();
        String uri = res.get("uri").get("value").asText();
        try {
          term.uri = new URI(uri);
          term.definition = getOptional(res, "hiddenLabel");
          term.altLabel = getOptional(res, "altLabel");
          term.prefLabel = Lists.newArrayList(res.get("prefLabel").get("value").textValue());
          term.scopeNote = getOptional(res, "scopeNote");
        } catch (URISyntaxException e) {
          LOG.error("Uri {} not an URI", uri);
        }

        terms.add(term);
      });

    } catch (IOException | SaxonApiException e) {
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
