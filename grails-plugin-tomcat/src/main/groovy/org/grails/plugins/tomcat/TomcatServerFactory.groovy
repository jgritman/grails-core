package org.grails.plugins.tomcat

import grails.web.container.EmbeddableServer
import grails.web.container.EmbeddableServerFactory
import org.grails.plugins.tomcat.fork.ForkedTomcatServer
import org.grails.plugins.tomcat.fork.TomcatExecutionContext
import org.codehaus.groovy.grails.cli.support.BuildSettingsAware
import grails.util.BuildSettings
import groovy.transform.CompileStatic
import grails.util.Environment
import org.codehaus.groovy.grails.cli.fork.ForkedGrailsProcess

class TomcatServerFactory implements EmbeddableServerFactory,BuildSettingsAware {

    BuildSettings buildSettings

    @CompileStatic
    EmbeddableServer createInline(String basedir, String webXml, String contextPath, ClassLoader classLoader) {
        return new InlineExplodedTomcatServer(basedir, webXml, contextPath, classLoader)
    }

    @CompileStatic
    private ForkedTomcatServer createForked(String contextPath) {
        TomcatExecutionContext ec = new TomcatExecutionContext()
        List<File> buildDependencies = buildMinimalIsolatedClasspath()

        ec.buildDependencies = buildDependencies
        ec.runtimeDependencies = buildSettings.runtimeDependencies
        ec.providedDependencies = buildSettings.providedDependencies
        ec.contextPath = contextPath
        ec.baseDir = buildSettings.baseDir
        ec.env = Environment.current.name
        ec.grailsHome = buildSettings.grailsHome
        ec.classesDir = buildSettings.classesDir
        ec.grailsWorkDir = buildSettings.grailsWorkDir
        ec.projectWorkDir = buildSettings.projectWorkDir
        ec.projectPluginsDir = buildSettings.projectPluginsDir
        ec.testClassesDir = buildSettings.testClassesDir
        ec.resourcesDir = buildSettings.resourcesDir

        return new ForkedTomcatServer(ec)
    }

    @CompileStatic
    private List<File> buildMinimalIsolatedClasspath() {
        List<File> buildDependencies = ForkedGrailsProcess.buildMinimalIsolatedClasspath(buildSettings)
        final tomcatJars = IsolatedWarTomcatServer.findTomcatJars(buildSettings)
        buildDependencies.addAll(tomcatJars.findAll { File f -> !f.name.contains('juli')})
        return buildDependencies
    }

    EmbeddableServer createForWAR(String warPath, String contextPath) {
        return new IsolatedWarTomcatServer(warPath, contextPath)
    }
}
