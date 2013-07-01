/* ****************************************************************************
Copyright 2013 Ellucian Company L.P. and its affiliates.
******************************************************************************/
package net.hedtech.restfulapi.marshallers.xml

import grails.converters.XML
import grails.util.GrailsNameUtils

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.codehaus.groovy.grails.web.converters.marshaller.xml.*
import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty
import org.codehaus.groovy.grails.web.converters.exceptions.ConverterException

import org.springframework.beans.BeanWrapper
import org.springframework.beans.BeanWrapperImpl

class DeclarativeDomainClassMarshaller extends BasicDomainClassMarshaller {
        protected static final Log log =
        LogFactory.getLog(DeclarativeDomainClassMarshaller.class)

    Class supportClass
    def fieldNames = [:]
    def includedFields = []
    def excludedFields = []
    def includeId = true
    def includeVersion = true
    def additionalFieldClosures = []
    def additionalFieldsMap = [:]
    def fieldResourceNames = [:]
    def shortObjectClosure = DEFAULT_SHORT_OBJECT

    private static DEFAULT_SHORT_OBJECT = { Map map ->
        def resource = map['resourceName']
        def id = map['resourceId']
        def xml = map['xml']
        xml.startNode("_link")
        xml.convertAnother("/$resource/$id")
        xml.end()
    }

    @Override
    public boolean supports(Object object) {
        if (supportClass) {
            supportClass.isInstance(object)
        } else {
            super.supports(object)
        }
    }

    /**
     * Return the name to use when marshalling the field, or
     * null if the field name should be used as-is.
     * @return the name to use when marshalling the field,
     *         or null if the domain field name should be used
     */
    @Override
    protected String getSubstitutionName(BeanWrapper beanWrapper,GrailsDomainClassProperty property) {
        return fieldNames.get( property.getName() )
    }

    /**
     * Returns the list of fields that should be marshalled
     * for the specified object.
     *<p>
     * If a null or zero-size list is returned, then
     * all fields except those specified by
     * {@link #getExcludedFields(Object) getExcludedFields} and
     * {@link #getCommonExcludedFields} will be marshalled.
     * If a non-zero sized list is returned, then only
     * the fields listed in it are marshalled.  Included fields
     * overrides any skipped fields.  That is, if a field is returned
     * by {@link getIncludedFields(Object) #getIncludedFields} then it
     * will be marshalled even if it is also returned by
     * {@link #getExcludedFields(Object) getExcludedFields} and
     * {@link #getCommonExcludedFields}
     *
     * @return list of field names to marshall
     */
    @Override
    protected List getIncludedFields(Object value) {
        return includedFields
    }


    /**
     * Returns a list of additional fields in the
     * object that should not be marshalled.
     * The complete list of skipped fields is the
     * union of getCommonSkippedFields() and
     * the list returned by this method.
     * Does not apply if {@link #getIncludedFields(Object) getIncludedFields} returns
     * a list containing one or more field names.
     *
     * @param value the object being marshalled
     * @return list of fields that should be skipped
     */
    @Override
    protected List getExcludedFields(Object value) {
        return excludedFields
    }

    /**
     * Override processing of fields.
     * @return true if the marshaller should handle the field in
     *         the default way, false if no further action should
     *         be taken for the field.
     *
     **/
    @Override
    protected boolean processField(BeanWrapper beanWrapper,
                                   GrailsDomainClassProperty property,
                                   XML xml) {
        true
    }

    @Override
    protected void processAdditionalFields(BeanWrapper beanWrapper, XML xml) {
        Map map = [:]
        map.putAll( additionalFieldsMap )
        map.putAll(
            [
                'grailsApplication':app,
                'beanWrapper':beanWrapper,
                'xml':xml
            ]
        )
        if (!map['resourceName']) {
            map['resourceName'] = getDerivedResourceName(beanWrapper)
        }
        GrailsDomainClass domainClass = app.getDomainClass(beanWrapper.getWrappedInstance().getClass().getName())
        map['resourceId'] = beanWrapper.getPropertyValue(domainClass.getIdentifier().getName())
        additionalFieldClosures.each { c ->
            c.call( map )
        }
    }

    /**
     * Override whether to include an 'id' field
     * for the specified value.
     * @param o the value
     * @return true if an 'id' field should be placed in the
     *         representation
     **/
    @Override
    protected boolean includeIdFor(Object o) {
        return includeId
    }

    /**
     * Override whether to include a 'version' field
     * for the specified value.
     * @param o the value
     * @return true if a 'version' field should be placed in the
     *         representation
     **/
    @Override
    protected boolean includeVersionFor(Object o) {
        return includeVersion
    }

    /**
     * Marshalls an object reference as a json object
     * containing a link to the referenced object as a
     * resource url.
     * @param property the property containing the reference
     * @param refObj the referenced object
     * @param json the JSON converter to marshall to
     */
    @Override
    protected void asShortObject(GrailsDomainClassProperty property, Object refObj, XML xml) throws ConverterException {
        GrailsDomainClass refDomainClass = property.getReferencedDomainClass()
        Object id = extractIdForReference( refObj, refDomainClass )
        def resource = fieldResourceNames[property.getName()]
        if (resource == null) {
            def domainName = GrailsNameUtils.getPropertyName(refDomainClass.shortName)
            resource = hyphenate(pluralize(domainName))
        }
        Map map = [
            grailsApplication:app,
            property:property,
            refObject:refObj,
            xml:xml,
            resourceId:id,
            resourceName:resource
        ]
        this.shortObjectClosure.call(map)
    }
}