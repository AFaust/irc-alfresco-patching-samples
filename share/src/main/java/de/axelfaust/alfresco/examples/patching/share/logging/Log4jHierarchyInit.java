package de.axelfaust.alfresco.examples.patching.share.logging;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * Initialises Log4j's logger repository. The actual implementation uses introspection to avoid any hard-coded references to Log4J classes.
 * If Log4J is not present, this class will do nothing.
 * <p>
 * Alfresco modules can provide their own log4j.properties file, which augments/overrides the global log4j.properties within the Alfresco
 * webapp. Within the module's source tree, suppose you create:
 *
 * <pre>
 *      config/alfresco/module/{module.id}/log4j.properties
 * </pre>
 *
 * At deployment time, this log4j.properties file will be placed in:
 *
 * <pre>
 *      WEB-INF/classes/alfresco/module/{module.id}/log4j.properties
 * </pre>
 *
 * Where {module.id} is whatever value is set within the AMP's module.properties file. For details, see: <a
 * href='http://wiki.alfresco.com/wiki/Developing_an_Alfresco_Module'>Developing an Alfresco Module</a>
 * <p>
 * For example, if {module.id} is "org.alfresco.module.someModule", then within your source code you'll have:
 *
 * <pre>
 * config / alfresco / module / org.alfresco.module.someModule / log4j.properties
 * </pre>
 *
 * This would be deployed to:
 *
 * <pre>
 * WEB - INF / classes / alfresco / module / org.alfresco.module.someModule / log4j.properties
 * </pre>
 *
 *
 * This class is a near-verbatim copy from the {@link org.alfresco.repo.admin.Log4JHierarchyInit Repository-tier class}.
 *
 * @see org.alfresco.repo.admin.Log4JHierarchyInit
 */
public class Log4jHierarchyInit implements InitializingBean, ApplicationContextAware, BeanDefinitionRegistryPostProcessor
{

    private static Logger LOGGER = LoggerFactory.getLogger(Log4jHierarchyInit.class);

    protected final List<String> extraLog4jUrls = new ArrayList<String>();

    protected ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    /**
     * {@inheritDoc}
     */
    @Override
    public void postProcessBeanFactory(final ConfigurableListableBeanFactory beanFactory) throws BeansException
    {
        // NO-OP - we only implement the interface to be instantiated as early as possible in Spring lifecycle
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void postProcessBeanDefinitionRegistry(final BeanDefinitionRegistry registry) throws BeansException
    {
        // NO-OP - we only implement the interface to be instantiated as early as possible in Spring lifecycle
    }

    /**
     * Loads a set of augmenting/overriding log4j.properties files from locations specified via an array of Srping URLS.
     * <p>
     * This function supports Spring's syntax for retrieving multiple class path resources with the same name, via the "classpath&#042;:"
     * prefix. For details, see: {@link PathMatchingResourcePatternResolver}.
     */
    public void setExtraLog4jUrls(final List<String> urls)
    {
        for (final String url : urls)
        {
            this.extraLog4jUrls.add(url);
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException
    {
        this.resolver = applicationContext;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        this.importLogSettings();
    }

    protected void importLogSettings()
    {
        try
        {
            // Get the PropertyConfigurator
            final Class<?> clazz = Class.forName("org.apache.log4j.PropertyConfigurator");
            final Method method = clazz.getMethod("configure", URL.class);
            // Import using this method
            for (final String url : this.extraLog4jUrls)
            {
                this.importLogSettings(method, url);
            }
        }
        catch (final ClassNotFoundException e)
        {
            // Log4J not present
            return;
        }
        catch (final NoSuchMethodException e)
        {
            throw new RuntimeException("Unable to find method 'configure' on class 'org.apache.log4j.PropertyConfigurator'");
        }

    }

    protected void importLogSettings(final Method method, final String springUrl)
    {
        Resource[] resources = null;

        try
        {
            resources = this.resolver.getResources(springUrl);
        }
        catch (final Exception e)
        {
            LOGGER.warn("Failed to find additional Logger configuration: {}", springUrl);
        }

        if (resources != null)
        {
            // Read each resource
            for (final Resource resource : resources)
            {
                try
                {
                    final URL url = resource.getURL();
                    method.invoke(null, url);
                }
                catch (final Throwable e)
                {
                    LOGGER.debug("Failed to add extra Logger configuration: \n   URL:   {}\n   Error: {}", springUrl, e);
                }
            }
        }
    }
}
