/*
 * Copyright 2012 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.plugins.tomcat.fork

import grails.util.BuildSettings
import grails.util.BuildSettingsHolder
import grails.web.container.EmbeddableServer
import groovy.transform.CompileStatic
import org.apache.catalina.Context
import org.codehaus.groovy.grails.cli.fork.ExecutionContext
import org.codehaus.groovy.grails.cli.fork.ForkedGrailsProcess
import org.codehaus.groovy.grails.io.support.Resource
import org.codehaus.groovy.grails.plugins.GrailsPluginUtils

import java.lang.reflect.Method
import org.grails.plugins.tomcat.InlineExplodedTomcatServer

/**
 * An implementation of the Tomcat server that runs in forked mode.
 *
 * @author Graeme Rocher
 * @since 2.2
 */
class ForkedTomcatServer extends ForkedGrailsProcess implements EmbeddableServer {

    @Delegate TomcatRunner tomcatRunner
    TomcatExecutionContext executionContext

    ForkedTomcatServer(TomcatExecutionContext executionContext) {
        this.executionContext = executionContext
    }

    private ForkedTomcatServer() {
        executionContext = (TomcatExecutionContext)readExecutionContext()
        if (executionContext == null) {
            throw new IllegalStateException("Forked server created without first creating execution context and calling fork()")
        }
    }

    static void main(String[] args) {
        new ForkedTomcatServer().run()
    }

    def run() {
        TomcatExecutionContext ec = executionContext
        def buildSettings = new BuildSettings(ec.grailsHome, ec.baseDir)
        buildSettings.loadConfig()

        BuildSettingsHolder.settings = buildSettings

        def urls = buildSettings.runtimeDependencies.collect { File f -> f.toURL() }
        urls.add(buildSettings.classesDir.toURL())
        urls.add(buildSettings.pluginClassesDir.toURL())
        urls.add(buildSettings.pluginBuildClassesDir.toURL())
        urls.add(buildSettings.pluginProvidedClassesDir.toURL())

        URLClassLoader classLoader = new URLClassLoader(urls as URL[])

        initializeLogging(ec.grailsHome,classLoader)

        tomcatRunner = new TomcatRunner("$buildSettings.baseDir/web-app", buildSettings.webXmlLocation.absolutePath, ec.contextPath, classLoader)
        tomcatRunner.start(ec.host, ec.port)

    }

    protected void initializeLogging(File grailsHome, ClassLoader classLoader) {
        try {
            Class<?> cls = classLoader.loadClass("org.apache.log4j.PropertyConfigurator");
            Method configure = cls.getMethod("configure", URL.class);
            configure.setAccessible(true);
            File f = new File(grailsHome.absolutePath + "/scripts/log4j.properties");
            configure.invoke(cls, f.toURI().toURL());
        } catch (Throwable e) {
            println("Log4j was not found on the classpath and will not be used for command line logging. Cause "+e.getClass().getName()+": " + e.getMessage());
        }
    }

    @CompileStatic
    void start(String host, int port) {
        final ec = executionContext
        ec.host = host
        ec.port = port
        def t = new Thread( {
            fork()
        } )

        t.start()
        while(!isAvailable(host, port)) {
            sleep 100
        }
    }

    @CompileStatic
    boolean isAvailable(String host, int port) {
        try {
            new Socket(host, port)
            return true
        } catch (e) {
            return false
        }
    }

    @Override
    ExecutionContext createExecutionContext() {
        return executionContext
    }

    void stop() {
        try {
            new URL("http://${executionContext?.host}:${executionContext?.port  + 1}").text
        } catch(e) {
            // ignore
        }
    }

    class TomcatRunner extends InlineExplodedTomcatServer {

        private String currentHost
        private int currentPort

        TomcatRunner(String basedir, String webXml, String contextPath, ClassLoader classLoader) {
            super(basedir, webXml, contextPath, classLoader)
        }

        @Override
        protected void configureAliases(Context context) {
            def aliases = []
            final directories = GrailsPluginUtils.getPluginDirectories()
            for (Resource dir in directories) {
                def webappDir = new File("${dir.file.absolutePath}/web-app")
                if (webappDir.exists()) {
                    aliases << "/plugins/${dir.file.name}=${webappDir.absolutePath}"
                }
            }
            if (aliases) {
                context.setAliases(aliases.join(','))
            }
        }

        @Override
        void start(String host, int port) {
            currentHost = host
            currentPort = port
            super.start(host, port)
        }

        @Override
        void stop() {
            try {
                new URL("http://${currentHost}:${currentPort+ 1}").text
            } catch(e) {
                // ignore
            }
        }
    }
}

class TomcatExecutionContext extends ExecutionContext implements Serializable {
    String contextPath
    String host
    int port
    int securePort
    File grailsHome
}
