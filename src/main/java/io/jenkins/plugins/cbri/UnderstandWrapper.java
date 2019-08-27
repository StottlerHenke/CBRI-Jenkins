package io.jenkins.plugins.cbri;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * A wrapper to run the Understand application on the given files.
 */
public class UnderstandWrapper {

    private String undPath;
    private String undPerl;
    private String pluginPath;


    public UnderstandWrapper(String undPath, String undPerl, String pluginPath) {

        this.undPath = undPath;
        this.undPerl = undPerl;
        this.pluginPath = pluginPath;
    }

    /**
     *
     * @return true if this is a windows system
     */
    public boolean isWindows() {

        return System.getProperty("os.name").toLowerCase().startsWith("windows");
    }

    /**
     *
     * @return Understand metrics if generated; an exception otherwise
     */
    public CbriAction runUnderstand(String language, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {

        // Perform analysis
        String undDb = workspace + "/understand.udb";
        String undCommand = undPath +
        " -quiet create -languages " + language +
        " add " + workspace +
        " analyze " + undDb;
        int exitCode = runCommand(undCommand, listener);
        if(exitCode != 0)
            throw new IOException("Understand analysis failed.");
        else
            listener.getLogger().println("\tUnderstand analysis succeeded");


        // Run core metrics
        String coreDir = workspace + "/understand";
        String uperlCommand = undPerl + " " +
                pluginPath +  " -db " + undDb +
                " -createMetrics -DuplicateMinLines 10 -outputDir " + coreDir;
        exitCode = runCommand(uperlCommand, listener);
        if(exitCode != 0)
            throw new IOException("Understand core metrics failed.");
        else
            listener.getLogger().println("\tUnderstand core metrics succeeded");


        // Read metrics in from a file and return the them
        CbriMetrics metrics = new CbriMetrics();
        return metrics.loadMetrics(workspace, listener);
    }

    /**
     * Run a command line process and log the output from the process.
     */
    public int runCommand(String command, TaskListener listener) throws IOException, InterruptedException {

        //listener.getLogger().println("Run Command: " + command);

        //May say 'This license has expired.' while still returning error code 0. In this case, all is not well.
        /*Process process = Runtime.getRuntime().exec(command);
        int exitCode = process.waitFor();

        String line;
        BufferedReader error = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
        while((line = error.readLine()) != null){
            listener.getLogger().println(line);
            if(line.contains("license")) exitCode = -1;
        }
        error.close();

        BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        while((line=input.readLine()) != null){
            listener.getLogger().println(line);
            if(line.contains("license")) exitCode = -1;
        }
        input.close();

        return exitCode;
        */

        listener.getLogger().println("The pipeline should run this command BEFORE CbriBuilder:");
        listener.getLogger().println(command);
        listener.getLogger().println();

        return 0;
    }
}
