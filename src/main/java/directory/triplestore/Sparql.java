package directory.triplestore;

import java.io.ByteArrayOutputStream;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryException;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RDFWriter;
import org.apache.jena.sparql.resultset.ResultsFormat;
import org.apache.jena.update.UpdateExecutionFactory;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateProcessor;
import org.apache.jena.update.UpdateRequest;
import org.eclipse.jetty.client.HttpClient;

import directory.Directory;
import directory.Utils;
import directory.exceptions.RemoteSparqlEndpointException;

public class Sparql {

	/*static {
		startEmbeddedSparql("hive");
	}*/
	
	private Sparql() {
		super();
	}
	
	public static ResultsFormat guess(String str) {
		return ResultsFormat.lookup(str);
	}
	// 
	
	// query methods
	
	public static ByteArrayOutputStream query(String sparql, ResultsFormat format) {
		return  query(sparql, format, Directory.getConfiguration().getTriplestore().getQueryEnpoint().toString(), Directory.getConfiguration().getTriplestore().getUsername(), Directory.getConfiguration().getTriplestore().getPassword());
	}
	
	public static ByteArrayOutputStream query(String sparql, ResultsFormat format, String endpoint, String username, String password) {

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		try {
			Query query = QueryFactory.create(sparql) ;
			QueryExecution qexec = QueryExecutionFactory.sparqlService(endpoint, query);
			if(username!= null && password!=null) {
				CloseableHttpClient client = connectPW(endpoint, username,password);
				qexec = QueryExecutionFactory.sparqlService(Directory.getConfiguration().getTriplestore().getQueryEnpoint().toString(), query, client);        
			}

			if(query.isSelectType()) {
				ResultSetFormatter.output(stream, qexec.execSelect(), format);
	        }else if(query.isAskType()) {
				ResultSetFormatter.output(stream, qexec.execAsk(), ResultsFormat.convert(format));
	        }else if(query.isConstructType()) {
	        	RDFFormat formatOutput = RDFFormat.NT;
	        	if(ResultsFormat.FMT_RDF_JSONLD.equals(format)) formatOutput = RDFFormat.JSONLD;
	        	if(ResultsFormat.FMT_RDF_TURTLE.equals(format)) formatOutput = RDFFormat.TURTLE;
	        	if(ResultsFormat.FMT_RDF_NT.equals(format)) formatOutput = RDFFormat.NTRIPLES;
	        	if(ResultsFormat.FMT_RDF_NQ.equals(format)) formatOutput = RDFFormat.NQ;
	        		
	        	RDFWriter.create(qexec.execConstruct()).format(formatOutput).output(stream);
	        }else if(query.isDescribeType()) {
	        	RDFFormat formatOutput = RDFFormat.NT;
	        	if(ResultsFormat.FMT_RDF_JSONLD.equals(format)) formatOutput = RDFFormat.JSONLD;
	        	if(ResultsFormat.FMT_RDF_TURTLE.equals(format)) formatOutput = RDFFormat.TURTLE;
	        	if(ResultsFormat.FMT_RDF_NT.equals(format)) formatOutput = RDFFormat.NTRIPLES;
	        	if(ResultsFormat.FMT_RDF_NQ.equals(format)) formatOutput = RDFFormat.NQ;
	        	RDFWriter.create(qexec.execDescribe()).format(formatOutput).output(stream);
	        }else {
	        	throw new RemoteSparqlEndpointException("Query not supported, provided one query SELECT, ASK, DESCRIBE or CONSTRUCT");
	        }
		}catch(QueryException e) {
			String msg = "Internal query has syntax errors: "+ e.toString();
			Directory.LOGGER.error("ERROR: "+msg);
			Directory.LOGGER.error("ERROR: "+sparql);
			throw new RemoteSparqlEndpointException(msg);
        }catch(Exception e) {
        	throw new RemoteSparqlEndpointException(e.toString());
        }
        return stream;
	}
	
	public static CloseableHttpClient connectPW(String URL, String user, String password) {
		  BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
		  Credentials credentials = new UsernamePasswordCredentials(user, password);
		  credsProvider.setCredentials(AuthScope.ANY, credentials);
		  return  HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();

	}
	
	public static void update(String sparql ) { 
		update( sparql,  Directory.getConfiguration().getTriplestore().getUpdateEnpoint().toString(), Directory.getConfiguration().getTriplestore().getUsername(), Directory.getConfiguration().getTriplestore().getPassword());
	}
	
	public static void update(String sparql, String endpoint, String username, String password)  {
		try {
			UpdateRequest updateRequest = UpdateFactory.create(sparql);
			UpdateProcessor updateProcessor = UpdateExecutionFactory.createRemote(updateRequest, endpoint);
			
			if(username!=null && password!=null) {
				CloseableHttpClient client = connectPW(endpoint,username, password);
				updateProcessor = UpdateExecutionFactory.createRemote(updateRequest, endpoint, client);
			}	
			updateProcessor.execute();
		 }catch(QueryException e){
	        throw new RemoteSparqlEndpointException(e.toString()); // syntax error
		}catch(org.apache.jena.atlas.web.HttpException e) {
			throw new RemoteSparqlEndpointException(e.getMessage());
		}
	}
	

	
	
}
