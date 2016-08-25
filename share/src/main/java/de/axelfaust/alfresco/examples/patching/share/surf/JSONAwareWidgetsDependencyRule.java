package de.axelfaust.alfresco.examples.patching.share.surf;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.alfresco.util.PropertyCheck;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.extensions.surf.DojoDependencies;
import org.springframework.extensions.surf.DojoDependencyRule;
import org.springframework.util.StringUtils;

/**
 * This special rule implementation will attempt to parse and process the declarative widget model of a page as JSON before falling back to
 * the less efficient and more error prone default Regex evaluation. Tests have shown that the Regex evaluation scales non-linearily with
 * increasing size / depth of the declarative model and can take multiple and even a two-digit amount of seconds to complete.
 *
 * @author Axel Faust
 */
public class JSONAwareWidgetsDependencyRule extends DojoDependencyRule implements InitializingBean
{

    private static final String WIDGET_CONFIG = "config";

    private static final String WIDGET_NAME = "name";

    private static final Logger LOGGER = LoggerFactory.getLogger(JSONAwareWidgetsDependencyRule.class);

    protected String widgetKeyRegex = "^(widgets?([A-Z].+)?|.+Widgets?)$";

    protected Pattern widgetKeyPattern;

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "widgetKeyRegex", this.widgetKeyRegex);
        this.widgetKeyPattern = Pattern.compile(this.widgetKeyRegex);
    }

    /**
     * @param widgetKeyRegex
     *            the widgetKeyRegex to set
     */
    public void setWidgetKeyRegex(final String widgetKeyRegex)
    {
        this.widgetKeyRegex = widgetKeyRegex;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void processRegexRules(final String filePath, final String fileContents, final DojoDependencies dependencies)
    {
        if (filePath == null)
        {
            Object jsonModel = null;
            try
            {
                jsonModel = this.tryParseAsJSON(fileContents);
            }
            catch (final JSONException e)
            {
                LOGGER.debug("Failed to parse potential JSON model - falling back to regex evaluation", e);
            }

            if (!(jsonModel instanceof JSONObject || jsonModel instanceof JSONArray))
            {
                this.processRegexRulesImpl(null, fileContents, dependencies);
            }
            else
            {
                if (jsonModel instanceof JSONObject)
                {
                    this.processModelImpl((JSONObject) jsonModel, dependencies, fileContents);
                }
                else
                {
                    this.processModelsImpl((JSONArray) jsonModel, dependencies, fileContents);
                }
            }
        }
        else
        {
            this.processRegexRulesImpl(filePath, fileContents, dependencies);
        }
    }

    protected Object tryParseAsJSON(final String fileContents) throws JSONException
    {
        final Object json;

        if (fileContents.startsWith("{"))
        {
            json = new JSONObject(new JSONTokener(fileContents));
        }
        else if (fileContents.startsWith("["))
        {
            json = new JSONArray(new JSONTokener(fileContents));
        }
        else
        {
            LOGGER.debug("Unable to determine if JSON should be parsed as array or object");
            json = null;
        }

        return json;
    }

    protected void processModelImpl(final JSONObject jsonModel, final DojoDependencies dependencies, final String fileContents)
    {
        try
        {
            final String[] names = JSONObject.getNames(jsonModel);
            // null as return value for empty object is evil - should've been empty array
            if (names != null)
            {
                for (final String key : names)
                {
                    final Object value = jsonModel.get(key);

                    if (this.widgetKeyPattern.matcher(key).matches())
                    {
                        if (value instanceof JSONObject)
                        {
                            this.processSingleWidgetImpl((JSONObject) value, dependencies, fileContents);
                        }
                        else if (value instanceof JSONArray)
                        {
                            this.processMultiWidgetsImpl((JSONArray) value, dependencies, fileContents);
                        }
                    }
                    else
                    {
                        if (value instanceof JSONObject)
                        {
                            this.processModelImpl((JSONObject) value, dependencies, fileContents);
                        }
                        else if (value instanceof JSONArray)
                        {
                            this.processModelsImpl((JSONArray) value, dependencies, fileContents);
                        }
                    }
                }
            }
        }
        catch (final JSONException e)
        {
            LOGGER.warn("Error processing JSON model - falling back to regex rules", e);
            this.processRegexRulesImpl(null, fileContents, dependencies);
        }
    }

    protected void processModelsImpl(final JSONArray jsonModels, final DojoDependencies dependencies, final String fileContents)
    {
        try
        {
            for (int idx = 0; idx < jsonModels.length(); idx++)
            {
                final Object element = jsonModels.get(idx);
                if (element instanceof JSONObject)
                {
                    this.processModelImpl((JSONObject) element, dependencies, fileContents);
                }
                else if (element instanceof JSONArray)
                {
                    this.processModelsImpl((JSONArray) element, dependencies, fileContents);
                }
            }
        }
        catch (final JSONException e)
        {
            LOGGER.warn("Error processing JSON models - falling back to regex rules", e);
            this.processRegexRulesImpl(null, fileContents, dependencies);
        }
    }

    protected void processMultiWidgetsImpl(final JSONArray widgetsModel, final DojoDependencies dependencies, final String fileContents)
    {
        for (int idx = 0; idx < widgetsModel.length(); idx++)
        {
            final JSONObject widget = widgetsModel.optJSONObject(idx);
            if (widget != null)
            {
                this.processSingleWidgetImpl(widget, dependencies, fileContents);
            }
        }
    }

    protected void processSingleWidgetImpl(final JSONObject widgetModel, final DojoDependencies dependencies, final String fileContents)
    {
        // TODO Do we want to make name / config key names configurable? Unlikely to be different than these defaults
        final String widgetName = widgetModel.optString(WIDGET_NAME);
        if (widgetName != null)
        {
            final JSONObject configModel = widgetModel.optJSONObject(WIDGET_CONFIG);
            if (configModel != null)
            {
                this.processModelImpl(configModel, dependencies, fileContents);
            }

            final String depPath = this.getDojoDependencyHandler().getPath(null, widgetName) + ".js";
            this.addJavaScriptDependency(dependencies, depPath);
        }
        else
        {
            this.processModelImpl(widgetModel, dependencies, fileContents);
        }
    }

    protected void processRegexRulesImpl(final String filePath, final String fileContents, final DojoDependencies dependencies)
    {
        final Matcher m1 = this.getDeclarationRegexPattern().matcher(fileContents);
        while (m1.find())
        {
            if (m1.groupCount() >= this.getTargetGroup())
            {
                // The second group in a regex match will contain array of dependencies...
                final String deps = m1.group(this.getTargetGroup());
                if (deps != null)
                {
                    // Recursively look for nested widgets...
                    this.processRegexRulesImpl(filePath, deps, dependencies);

                    // Find the dependencies in the widgets list...
                    final Matcher m2 = this.getDependencyRegexPattern().matcher(deps);
                    while (m2.find())
                    {
                        final String dep = m2.group(1);
                        if (dep != null)
                        {
                            // convert dep found in JSON to valid file path - as JSON may encode "/" as "\/"
                            final String depPath = this.getDojoDependencyHandler().getPath(filePath, StringUtils.replace(dep, "\\/", "/"))
                                    + ".js";
                            this.addJavaScriptDependency(dependencies, depPath);
                        }
                    }
                }
            }
        }
    }
}