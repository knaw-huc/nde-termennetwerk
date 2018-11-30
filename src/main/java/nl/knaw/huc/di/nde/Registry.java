package nl.knaw.huc.di.nde;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import static graphql.ExecutionInput.newExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLException;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import nl.knaw.huc.di.nde.recipe.RecipeInterface;
import nl.mpi.tla.util.Saxon;

@Path("graphql")
public class Registry {
    
    private final String schema;
    private final ObjectMapper objectMapper;
    private final GraphQL.Builder builder;

    final static public Map<String,String> NAMESPACES = new LinkedHashMap<>();
    static {
        NAMESPACES.put("dc",      "http://purl.org/dc/elements/1.1/");
        NAMESPACES.put("dcterms", "http://purl.org/dc/terms/");
        NAMESPACES.put("nde",     "https://www.netwerkdigitaalerfgoed.nl/");
        NAMESPACES.put("rdf",     "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        NAMESPACES.put("skos",    "http://www.w3.org/2004/02/skos/core#");
    };

    public Registry() throws IOException {
        this.schema = Resources.toString(Registry.class.getResource("/nl/knaw/huc/di/nde/registry/schema.graphql"), Charsets.UTF_8);
        this.objectMapper = new ObjectMapper();
        // schema
        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeRegistry = schemaParser.parse(this.schema);
        // fetching
        DataFetcher termsDataFetcher = new DataFetcher<List<DatasetDTO>>() {
            @Override
            public List<DatasetDTO> get(DataFetchingEnvironment environment) {
                List<DatasetDTO> datasets = new ArrayList<DatasetDTO>();
                List<TermDTO> terms = null;
                String match = environment.getArgument("match");
                List<String> sets = environment.getArgument("dataset");
                System.err.println("DBG: datasets["+sets+"]");
                if (match!=null && sets!=null) {
                    for (String set:sets)
                        datasets.add(fetchMatchingTerms(match,set));
                }
                return datasets;
            }
        };
        // wiring
        final RuntimeWiring runtime = RuntimeWiring.newRuntimeWiring()
                .type("Query", builder -> builder.defaultDataFetcher(termsDataFetcher))
                .build();
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        builder = GraphQL.newGraphQL(schemaGenerator.makeExecutableSchema(typeRegistry, runtime));
    }
    
    private DatasetDTO fetchMatchingTerms(String match,String dataset) {
        DatasetDTO res = new DatasetDTO();
        res.dataset = dataset;
        List<TermDTO> terms = new ArrayList<>();
        try {
            System.err.println("DBG: config["+System.getProperty("nde.config")+"]");
            System.err.println("DBG: match["+match+"]");
            System.err.println("DBG: dataset["+dataset+"]");
            XdmNode config = Saxon.buildDocument(new StreamSource(System.getProperty("nde.config")));
            Map vars = new HashMap();
            vars.put("dataset", new XdmAtomicValue(dataset));
            XdmItem dsConfig = Saxon.xpathSingle(config, "//nde:dataset[@id=$dataset]", vars, NAMESPACES);
            if (dsConfig != null) {
                res.label = new ArrayList<>();
                for (Iterator<XdmItem> lblIter = Saxon.xpathIterator(dsConfig, "nde:label",null, Registry.NAMESPACES); lblIter.hasNext();) {
                    res.label.add(lblIter.next().getStringValue());
                }
                String recipe = Saxon.xpath2string(dsConfig, "@recipe", null, NAMESPACES);
                if (!recipe.isEmpty()) {
                    Class<RecipeInterface> clazz = (Class<RecipeInterface>) Class.forName(recipe);
                    RecipeInterface recipeImpl = clazz.newInstance();
                    terms = recipeImpl.fetchMatchingTerms(dsConfig, match);
                } else
                    Logger.getLogger(Registry.class.getName()).log(Level.SEVERE,"Recipe of dataset["+dataset+"] is unknown!");
            } else
                Logger.getLogger(Registry.class.getName()).log(Level.SEVERE,"Unknown dataset["+dataset+"]!");
        } catch (Exception ex) {
            Logger.getLogger(Registry.class.getName()).log(Level.SEVERE, null, ex);
        }
        res.terms = terms;
        return res;
    }
    
    // GraphQL
    
    @POST
    @Consumes("application/json")
    public Response postJson(JsonNode body, @QueryParam("query") String query,
                           @HeaderParam("accept") String acceptHeader,
                           @QueryParam("accept") String acceptParam,
                           @HeaderParam("Authorization") String authHeader) {
        final String queryFromBody;
        if (body.has("query")) {
            queryFromBody = body.get("query").asText();
        } else {
            queryFromBody = null;
        }
        Map variables = null;
        if (body.has("variables")) {
            try {
                variables = objectMapper.treeToValue(body.get("variables"), HashMap.class);
            } catch (JsonProcessingException e) {
                return Response
                    .status(400)
                    .entity("'variables' should be an object node")
                    .build();
            }
        }
        final String operationName = body.has("operationName") && !body.get("operationName").isNull() ?
            body.get("operationName").asText() : null;

        return executeGraphql(query, acceptHeader, acceptParam, queryFromBody, variables, operationName, authHeader);
    }

    @POST
    @Consumes("application/graphql")
    public Response postGraphql(String query, @QueryParam("query") String queryParam,
                                @HeaderParam("accept") String acceptHeader,
                                @QueryParam("accept") String acceptParam,
                                @HeaderParam("Authorization") String authHeader) {
        return executeGraphql(queryParam, acceptHeader, acceptParam, query, null, null, authHeader);
    }

    @GET
    public Response get(@QueryParam("query") String query, @HeaderParam("accept") String acceptHeader,
                        @QueryParam("accept") String acceptParam,
                        @HeaderParam("Authorization") String authHeader) {
        return executeGraphql(null, acceptHeader, acceptParam, query, null, null, authHeader);
    }

    public Response executeGraphql(String query, String acceptHeader, String acceptParam, String queryFromBody,
                                 Map variables, String operationName, String authHeader) {

        if (acceptParam != null && !acceptParam.isEmpty()) {
            acceptHeader = acceptParam; //Accept param overrules header because it's more under the user's control
        }
        if (unSpecifiedAcceptHeader(acceptHeader)) {
            acceptHeader = MediaType.APPLICATION_JSON;
        }
//        if (MediaType.APPLICATION_JSON.equals(acceptHeader)) {
//        } else {
//        }
        if (query != null && queryFromBody != null) {
          return Response
            .status(400)
            .type(MediaType.APPLICATION_JSON_TYPE)
            .entity("{\"errors\": [\"There's both a query as url paramater and a query in the body. Please pick one.\"]}")
            .build();
        }
        if (query == null && queryFromBody == null) {
          return Response
            .status(400)
            .type(MediaType.APPLICATION_JSON_TYPE)
            .entity("{\"errors\": [\"Please provide the graphql query as the query property of a JSON encoded object. " +
              "E.g. {query: \\\"{\\n  persons {\\n ... \\\"}\"]}")
            .build();
        }

        GraphQL graphQl = builder.build();

        try {
          final ExecutionResult result = graphQl
            .execute(newExecutionInput()
              .query(queryFromBody)
              .operationName(operationName)
              .variables(variables == null ? Collections.emptyMap() : variables)
              .build());
            return Response
              .ok()
              .type(MediaType.APPLICATION_JSON_TYPE)
              .entity(result.toSpecification())
              .build();
        } catch (GraphQLException e) {
            return Response.status(500).entity(e.getMessage()).build();
            // throw e;
        }
    }

    public boolean unSpecifiedAcceptHeader(@HeaderParam("accept") String acceptHeader) {
        return acceptHeader == null || acceptHeader.isEmpty() || "*/*".equals(acceptHeader);
    }
}
