/* ****************************************************************************
Copyright 2013 Ellucian Company L.P. and its affiliates.
******************************************************************************/

package net.hedtech.restfulapi.query

import grails.plugin.spock.IntegrationSpec

import groovy.sql.Sql

import net.hedtech.restfulapi.*

import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.commons.DefaultGrailsApplication

import spock.lang.*


class HQLBuilderSpec extends IntegrationSpec {

    def grailsApplication
    def dataSource


    def setup() { }

    def cleanup() {
        deleteThings()
    }


    def "Test extracting filters from query parameters"() {

        setup:
        // URL:  things?filter[0][field]=code&filter[1][value]=science&filter[0][operator]=eq&
        // filter[1][field]=description&filter[1][operator]=contains&filter[0][value]=ZZ&max=50
        Map params = [ 'pluralizedResourceName':'things',
                       'filter[0][field]':'code',
                       'filter[1][value]':'science',
                       'filter[0][operator]':'eq',
                       'filter[1][field]':'description',
                       'filter[1][operator]':'contains',
                       'filter[0][value]':'ZZ',
                       'max':'50'
                     ]

        when:
        def result = Filter.extractFilters( grailsApplication, params )

        then:
        result[0].field == 'code'
        result[0].operator == 'eq'
        result[0].value == 'ZZ'
        result[1].field == 'description'
        result[1].operator == 'contains'
        result[1].value == 'science'
    }


    def "Test reporting query parameters using unsupported filter terms"() {

        setup:
        // URL:  things?filter[0][field]=code&filter[1][value]=science&filter[0][operator]=eq&
        // filter[1][field]=description&filter[1][operator]=contains&filter[0][value]=ZZ&max=50
        Map params = [ 'pluralizedResourceName':'things',
                       'filter[0][field]':'code',
                       'filter[1][value]':'science',
                       'filter[0][operator]':'eq',
                       'filter[1][field]':'description',
                       'filter[1][operator]':'contains',
                       'filter[0][voodoo]':'ZZ',
                       'max':'50'
                     ]

        when:
        def result = Filter.extractFilters( grailsApplication, params )

        then:
        !result[0].isValid()
        result[1].isValid()

        result[0].field == 'code'
        result[0].operator == 'eq'
        result[0].value == null
        result[1].field == 'description'
        result[1].operator == 'contains'
        result[1].value == 'science'
    }


    def "Test reporting query parameters using unsupported filter operators"() {

        setup:
        // URL:  things?filter[0][field]=code&filter[1][value]=science&filter[0][operator]=eq&
        // filter[1][field]=description&filter[1][operator]=contains&filter[0][value]=ZZ&max=50
        Map params = [ 'pluralizedResourceName':'things',
                       'filter[0][field]':'code',
                       'filter[1][value]':'science',
                       'filter[0][operator]':'startsWith', // not supported!
                       'filter[1][field]':'description',
                       'filter[1][operator]':'contains',
                       'filter[0][value]':'ZZ',
                       'max':'50'
                     ]

        when:
        def result = Filter.extractFilters( grailsApplication, params )

        then:
        !result[0].isValid()
        result[1].isValid()
    }


    def "Test determining the domain class corresponding to the resource"() {

        when:
        def grailsClass = Filter.getGrailsDomainClass( grailsApplication, 'things' )

        then:
        Thing == grailsClass.clazz
    }


    def "Test creating unfiltered query"() {

        setup:
        // URL: things?max=50
        Map params = [ 'pluralizedResourceName':'things' ]

        when:
        def query = HQLBuilder.createHQL( grailsApplication, params )

        then:
        'SELECT a FROM Thing a' == query.statement
    }


    def "Test creating unfiltered query with paging"() {

        setup:
        for (n in 'A'..'K') { createThing(n) }

        // URL: things?max=50&offset=0&sort=dec&order=code
        Map params = [ 'pluralizedResourceName':'things', 'max':'5', 'offset':'0',
                       'sort':'code', 'order':'desc' ]

        when:
        def query = HQLBuilder.createHQL( grailsApplication, params )

        then:
        "SELECT a FROM Thing a ORDER BY a.code desc" == query.statement
        def things = Thing.executeQuery( query.statement, query.parameters, params )
        things?.size == 5
        things[0].code == 'K'
        things[4].code == 'G'
    }


    def "Test creating simple filtered query"() {

        setup:
        def xxId = createThing('XX')
        def yyId = createThing('yy')

        // URL: things?filter[0][field]=code&filter[1][value]=science&filter[0][operator]=eq&
        // filter[1][field]=description&filter[1][operator]=contains&filter[0][value]=ZZ&max=50
        Map params = [ 'pluralizedResourceName':'things',
                       'filter[0][field]':'code',
                       'filter[1][value]':'An XX',
                       'filter[0][operator]':'eq',
                       'filter[1][field]':'description',
                       'filter[1][operator]':'contains',
                       'filter[0][value]':'XX',
                       'max':'50'
                     ]

        when:
        def query = HQLBuilder.createHQL( grailsApplication, params )

        then:
        "SELECT a FROM Thing a WHERE a.code = :code AND lower(a.description) LIKE lower(:description)" == query.statement

        def things = Thing.executeQuery( query.statement, query.parameters, [:] )
        'XX' == things[0].code
        'An XX thing' == things[0].description
    }


    def "Test overriding the domain class"() {

        setup:
        def xxId = createThing('XX')
        def yyId = createThing('yy')

        // URL: complex-things?filter[0][field]=code&filter[1][value]=science&filter[0][operator]=eq&
        // filter[1][field]=description&filter[1][operator]=contains&filter[0][value]=ZZ&max=50
        Map params = [ 'pluralizedResourceName':'complex-things',
                       'filter[0][field]':'code',
                       'filter[1][value]':'An XX',
                       'filter[0][operator]':'eq',
                       'filter[1][field]':'description',
                       'filter[1][operator]':'contains',
                       'filter[0][value]':'XX',
                       'max':'50'
                     ]

        when:
        params << [ domainClass: Filter.getGrailsDomainClass( grailsApplication, 'things' ) ]
        def query = HQLBuilder.createHQL( grailsApplication, params )

        then:
        "SELECT a FROM Thing a WHERE a.code = :code AND lower(a.description) LIKE lower(:description)" == query.statement
    }


    def "Test reporting bad query"() {

        setup:
        // URL: things?filter[0][field]=code&filter[1][value]=science&filter[0][operator]=eq&
        // filter[1][field]=description&filter[1][operator]=contains&filter[0][value]=ZZ&max=50
        Map params = [ 'pluralizedResourceName':'things',
                       'filter[0][field]':'you_will_not_find_me',
                       'filter[1][value]':'An XX',
                       'filter[0][operator]':'eq',
                       'filter[1][field]':'description',
                       'filter[1][operator]':'contains',
                       'filter[0][value]':'XX',
                       'max':'50'
                     ]

        when:
            def query = HQLBuilder.createHQL( grailsApplication, params )

        then:
            thrown BadFilterException
    }


    def "Test creating association filtered query"() {

        setup:
        def xxId = createThing('XX')
        def yyId = createThing('yy')

        // URL: things?filter[0][field]=description&filter[1][value]=6&filter[0][operator]=contains&
        // filter[1][field]=thing&filter[1][operator]=eq&filter[0][value]=yy&max=50
        Map params = [ 'pluralizedResourceName':'part-of-things',
                       'filter[0][field]':'description',
                       'filter[1][value]':"$yyId",
                       'filter[0][operator]':'contains',
                       'filter[1][field]':'thing',
                       'filter[1][operator]':'eq',
                       'filter[0][value]':'p1',
                       'max':'50'
                     ]

        when:
        def query = HQLBuilder.createHQL( grailsApplication, params )

        then:
        "SELECT a FROM PartOfThing a INNER JOIN a.thing b WHERE lower(a.description) LIKE lower(:description) AND b.id = :thing" == query.statement

        def things = Thing.executeQuery( query.statement, query.parameters, [:] )
        things?.size == 1
        things[0].code == 'p1'
        things[0].thing.id == yyId
    }


    def "Test creating nested resource filtered query"() {

        setup:
        def xxId = createThing('XX')
        def yyId = createThing('yy')

        // URL: things/6/part-of-things?filter[0][field]=description&filter[0][operator]=contains&
        // filter[0][value]=yy&max=50&offset=2
        Map params = [ 'pluralizedResourceName':'part-of-things',
                       'parentPluralizedResourceName':'things',
                       'parentId':"$yyId",
                       'filter[0][field]':'description',
                       'filter[0][operator]':'contains',
                       'filter[0][value]':'p2',
                       'max':'50',
                       'offset':'2',
                       'sort':'code'
                     ]

        when:
        def query = HQLBuilder.createHQL( grailsApplication, params )

        then:
        "SELECT a FROM PartOfThing a INNER JOIN a.thing b WHERE b.id = :thing AND lower(a.description) LIKE lower(:description) ORDER BY a.code" == query.statement

        def things = Thing.executeQuery( query.statement, query.parameters )
        things?.size == 1
        things[0].code == 'p2'
        things[0].thing.id == yyId
    }


    def "Test creating simple filtered count() query"() {

        setup:
        // URL: things?filter[0][field]=code&filter[1][value]=science&filter[0][operator]=eq&
        // filter[1][field]=description&filter[1][operator]=contains&filter[0][value]=ZZ&max=50
        Map params = [ 'pluralizedResourceName':'things',
                       'filter[0][field]':'code',
                       'filter[1][value]':'science',
                       'filter[0][operator]':'eq',
                       'filter[1][field]':'description',
                       'filter[1][operator]':'contains',
                       'filter[0][value]':'AA',
                       'max':'50'
                     ]

        when:
        def query = HQLBuilder.createHQL( grailsApplication, params, true /*count*/ )

        then:
        "SELECT count(a) FROM Thing a WHERE a.code = :code AND lower(a.description) LIKE lower(:description)" == query.statement
    }


    def "Test creating nested resource filtered count() query"() {

        setup:
        def xxId = createThing('XX')
        def yyId = createThing('yy')

        // URL: things/6/part-of-things?filter[0][field]=description&filter[0][operator]=contains&
        // filter[0][value]=yy&max=50&offset=2
        Map params = [ 'pluralizedResourceName':'part-of-things',
                       'parentPluralizedResourceName':'things',
                       'parentId':"$yyId",
                       'filter[0][field]':'description',
                       'filter[0][operator]':'contains',
                       'filter[0][value]':'p2',
                       'max':'50',
                       'offset':'2',
                       'sort':'code'
                     ]

        when:
        def query = HQLBuilder.createHQL( grailsApplication, params, true /*count*/ )

        then:
        "SELECT count(a) FROM PartOfThing a INNER JOIN a.thing b WHERE b.id = :thing AND lower(a.description) LIKE lower(:description)" == query.statement

        def count = Thing.executeQuery( query.statement, query.parameters )
        1 == count[0].toInteger()
    }


    // TODO: Not DRY with RestfulApiControllerFunctionalTests -- move to helper class...
    private def createThing( String code ) {
        Thing.withTransaction {
            Thing thing = new Thing( code: code, description: "An $code thing",
                      dateManufactured: new Date(), isGood: 'Y', isLarge: true )
                .addToParts( new PartOfThing( code: 'p1', description: "$code p1 part" ) )
                .addToParts( new PartOfThing( code: 'p2', description: "$code p2 part" ) )
                .save( failOnError:true, flush:true )
            thing.getId()
        }
    }

    private void deleteThings() {
        Thing.withNewSession {
            Thing.findAll().each() { it.delete( failOnError:true, flush:true ) }
        }
    }
}