package io.jenkins.plugins.cbri;

import hudson.model.TaskListener;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * A wrapper to post the CbriAction results (i.e. measurement) to the corresponding CBRI project.
 */
public class CbriWrapper {

    String LOGIN_PATH = "/login";
    String MEAUSREMENT_PREFIX = "/repositories/";
    String MEASUREMENT_SUFFIX = "/measurements/";


    protected static final String TOKEN_FIELD = "token";
    protected static final String USERNAME_FIELD = "username";
    protected static final String PASSWORD_FIELD = "password";
    protected static final String AUTH_FIELD = "Authorization";

    /**
     * We get this token after login
     */
    protected String authToken;

    /**
     * Base URL for CBRI.
     */
    protected WebTarget baseTarget;


    protected ObjectMapper mapper;
    String username;
    String password;
    String repoId;

    public CbriWrapper(String baseUrl, String username, String password, String repoId) {
        baseTarget = ClientBuilder.newClient().target(baseUrl);
        mapper = new ObjectMapper();
        this.username = username;
        this.password = password;
        this.repoId = repoId;
    }

    /**
     * Post the CbriAction to the project via the CBRI REST API
     */
    public void postAction(CbriAction action, TaskListener listener) throws IOException {

        boolean loggedIn = logIn(listener);
        if(!loggedIn) {
            throw new IOException("Failed to log into CBRI");
        }

        WebTarget checkTarget = baseTarget.path(MEAUSREMENT_PREFIX + repoId + MEASUREMENT_SUFFIX);
        Builder builder = checkTarget.request(MediaType.APPLICATION_JSON).header(AUTH_FIELD, "JWT " + authToken);
        Map<String, String> actionInfo = createMap(action);

        try {
            listener.getLogger().println("Attempting to post to CBRI: " + checkTarget.toString() );
            builder.post(Entity.json(mapper.writeValueAsString(actionInfo)), String.class);
            //listener.getLogger().println(responseStr);
        }
        catch(BadRequestException e) {
            listener.getLogger().println(e.getResponse().toString());
            listener.getLogger().println(e.getResponse().readEntity(String.class));
            throw new IOException(e);
        }
        catch(Exception e) {
            throw new IOException(e);
        }
    }

    /**
     * Copy data from the action to the map
     */
    protected Map<String, String> createMap(CbriAction action) {

        Map<String, String> actionInfo = new HashMap<>();

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'"); //"2018-06-14T22:00:00Z"
        TimeZone utc = TimeZone.getTimeZone("UTC");
        formatter.setTimeZone(utc);
        actionInfo.put("date", formatter.format(action.today));

        actionInfo.put("architecture_type", action.architectureType);
        if(action.isCore())
            actionInfo.put("is_core", "True");
        else
            actionInfo.put("is_core", "False");
        actionInfo.put("propagation_cost", "" + action.propagationCost);
        actionInfo.put("useful_lines_of_code", "" + action.uloc);
        actionInfo.put("num_classes", "" + action.numClasses);
        actionInfo.put("num_files", "" + action.numFiles);
        actionInfo.put("core_size", "" + action.coreSize);
        actionInfo.put("num_files_in_core", "" + action.numFilesInCore);
        actionInfo.put("num_files_overly_complex", "" + action.numFilesOverlyComplex);
        actionInfo.put("percent_files_overly_complex", "" + action.percentFilesOverlyComplex);
        actionInfo.put("useful_lines_of_comments", "" + action.usefulLinesComments);
        actionInfo.put("useful_comment_density", "" + action.usefulCommentDensity);
        actionInfo.put("duplicate_uloc", "" + action.duplicateUloc);
        actionInfo.put("percent_duplicate_uloc", "" + action.percentDuplicateUloc);

        return actionInfo;
    }

    /**
     * Check if we're logged in. If not, try to log in.
     * If that fails, return false and give up for now.
     */
    protected boolean logIn(TaskListener listener) throws IOException {
        boolean loggedIn = checkLoggedIn(listener);

        if(!loggedIn) {
            listener.getLogger().println("Attempting to log into CBRI");

            WebTarget loginTarget = baseTarget.path(LOGIN_PATH);
            listener.getLogger().println(loginTarget.toString());
            Builder builder = loginTarget.request(MediaType.APPLICATION_JSON);

            Map<String, String> loginInfo = new HashMap<>();
            loginInfo.put(USERNAME_FIELD, username);
            loginInfo.put(PASSWORD_FIELD, password);

            Map<String, String> response = null;

            try {
                String responseStr = builder.post(Entity.json(mapper.writeValueAsString(loginInfo)), String.class);
                response = mapper.readValue(responseStr, new TypeReference<HashMap>(){});
            } catch(Exception e) {
                listener.fatalError(e.toString());
            }

            if(response != null) {
                String token = response.get(TOKEN_FIELD);

                if(token != null) {
                    listener.getLogger().println("Logged into CBRI");
                    authToken = token;
                    loggedIn = true;
                }
            }
        }

        return loggedIn;
    }


    /**
     * Ask server if our authorization token is valid.
     * @return
     */
    protected boolean checkLoggedIn(TaskListener listener) throws IOException {
        boolean ret = false;

        if(authToken != null) {
            WebTarget checkTarget = baseTarget.path(LOGIN_PATH).path(authToken);
            Builder builder = checkTarget.request(MediaType.APPLICATION_JSON);

            Map<String, String> response = null;

            try {
                String responseStr = builder.get(String.class);
                response = mapper.readValue(responseStr, new TypeReference<HashMap>(){});
            } catch(NotFoundException e) {
                //Not a surprise - we used a token that isn't recognized
            }

            if(response != null) {
                ret = authToken.equals(response.get(TOKEN_FIELD));
            }
        }

        listener.getLogger().println("Check CBRI logged in: " + ret);
        return ret;
    }
}
