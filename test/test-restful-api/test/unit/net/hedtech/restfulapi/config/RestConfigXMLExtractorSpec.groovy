/* ****************************************************************************
Copyright 2013 Ellucian Company L.P. and its affiliates.
******************************************************************************/

package net.hedtech.restfulapi.config

import grails.test.mixin.*
import spock.lang.*

import net.hedtech.restfulapi.extractors.configuration.*
import net.hedtech.restfulapi.extractors.xml.*
import net.hedtech.restfulapi.*
import grails.test.mixin.support.*
import net.hedtech.restfulapi.marshallers.xml.*


@TestMixin(GrailsUnitTestMixin)
class RestConfigXMLExtractorSpec extends Specification {

    def "Test xml extractor template parsing"() {
        setup:
        def src =
        {
            xmlExtractorTemplates {
                template 'one' config {
                }

                template 'two' config {
                    inherits = ['one']
                    property 'person.name' name 'bar'
                    property 'person.address' flatObject true
                    property 'person.customer' shortObject true
                    property 'lastName' defaultValue 'Smith'
                    shortObject { def v -> 'short' }
                }
            }
        }

        when:
        def config = RestConfig.parse( grailsApplication, src )
        config.validate()
        def mConfig = config.xmlExtractor.configs['two']
        def shortObject = mConfig.shortObjectClosure.call([:])

        then:
         2                     == config.xmlExtractor.configs.size()
         ['person.name':'bar'] == mConfig.dottedRenamedPaths
         ['person.address']    == mConfig.dottedFlattenedPaths
         ['person.customer']   == mConfig.dottedShortObjectPaths
         ['lastName':'Smith']  == mConfig.dottedValuePaths
         'short'               == shortObject
    }

    def "Test xml extractor creation"() {
        setup:
        def src =
        {
            resource 'things' config {
                representation {
                    mediaTypes = ['application/xml']
                    xmlExtractor {
                        property 'person.name' name 'bar'
                        property 'person.address' flatObject true
                        property 'person.customer' shortObject true
                        property 'lastName' defaultValue 'Smith'
                        shortObject { def v -> 'short' }
                    }
                }
            }
        }

        when:
        def config = RestConfig.parse( grailsApplication, src )
        config.validate()
        def extractor = config.getRepresentation( 'things', 'application/xml' ).extractor
        def shortObject = extractor.shortObjectClosure.call([:])

        then:
         ['person.name':'bar'] == extractor.dottedRenamedPaths
         ['person.address']    == extractor.dottedFlattenedPaths
         ['person.customer']   == extractor.dottedShortObjectPaths
         ['lastName':'Smith']  == extractor.dottedValuePaths
         'short'               == shortObject
    }

    def "Test xml extractor creation from merged configuration"() {
        setup:
        def src =
        {
            xmlExtractorTemplates {
                template 'one' config {
                    property 'one' name 'modOne'
                }

                template 'two' config {
                    property 'two' name 'modTwo'
                }
            }

            resource 'things' config {
                representation {
                    mediaTypes = ['application/xml']
                    xmlExtractor {
                        inherits = ['one','two']
                        property 'three' name 'modThree'
                    }
                }
            }
        }

        when:
        def config = RestConfig.parse( grailsApplication, src )
        config.validate()
        def extractor = config.getRepresentation( 'things', 'application/xml' ).extractor

        then:
        ['one':'modOne','two':'modTwo','three':'modThree'] == extractor.dottedRenamedPaths
    }

}