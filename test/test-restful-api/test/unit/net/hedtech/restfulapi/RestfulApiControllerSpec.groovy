/* ****************************************************************************
Copyright 2013 Ellucian Company L.P. and its affiliates.
******************************************************************************/

package net.hedtech.restfulapi

import com.grailsrocks.cacheheaders.CacheHeadersService

import grails.test.mixin.*

import spock.lang.*

import net.hedtech.restfulapi.config.*
import net.hedtech.restfulapi.extractors.configuration.*
import net.hedtech.restfulapi.extractors.json.*
import net.hedtech.restfulapi.extractors.xml.*

@TestFor(RestfulApiController)
class RestfulApiControllerSpec extends Specification {

    def setup() {
        JSONExtractorConfigurationHolder.clear()
    }

    def cleanup() {
        JSONExtractorConfigurationHolder.clear()
    }

    @Unroll
    def "Test unsupported media type in Accept header returns 406"(String controllerMethod, String httpMethod, String id, String serviceMethod, def serviceReturn ) {
        setup:
        //use default extractor for any methods with a request body
         config.restfulApiConfig = {
            resource {
                name = 'things'
                representation {
                    mediaType = 'application/json'
                    extractor = new DefaultJSONExtractor()
                }
            }
        }
        controller.init()

        //mock the appropriate service method, expect exactly 1 invocation
        def mock = Mock(ThingService)
        controller.metaClass.getService = {-> mock}

        request.addHeader( 'Accept', 'application/xml' )
        //incoming format always json, so no errors
        request.addHeader( 'Content-Type', 'application/json' )
        request.method = httpMethod
        params.pluralizedResourceName = 'things'
        if (id != null) params.id = id

        when:
        controller."$controllerMethod"()

        then:
        406 == response.status
          0 == response.getContentLength()
          0 * _._

        where:
        controllerMethod | httpMethod | id   | serviceMethod | serviceReturn
        'list'           | 'GET'      | null | 'list'        | ['foo']
        'show'           | 'GET'      | '1'  | 'show'        | 'foo'
        'create'         | 'POST'     | null | 'create'      | 'foo'
        'update'         | 'PUT'      | '1'  | 'update'      | 'foo'
    }

    def "Test delete with unsupported Accept header works, as there is no content returned"() {
        setup:
        //use default extractor for any methods with a request body
        config.restfulApiConfig = {
            resource {
                name = 'things'
                representation {
                    mediaType = 'application/json'
                    extractor = new DefaultJSONExtractor()
                }
            }
        }

        controller.init()

        //mock the appropriate service method, expect exactly 1 invocation
        def mock = Mock(ThingService)
        controller.metaClass.getService = {-> mock}

        request.addHeader( 'Accept', 'application/xml' )
        //incoming format always json, so no errors
        request.addHeader( 'Content-Type', 'application/json' )
        request.method = 'DELETE'
        params.pluralizedResourceName = 'things'
        params.id = '1'

        when:
        controller.delete()

        then:
        200 == response.status
          0 == response.getContentLength()
          1 * mock.delete(_,_) >> {}
    }

    @Unroll
    def "Unsupported media type in Content-Type header returns 415"(String controllerMethod, String httpMethod, String id, String serviceMethod, def serviceReturn ) {
        setup:
         config.restfulApiConfig = {
            resource {
                name = 'things'
            }
        }
        controller.init()

        //mock the appropriate service method, but expect no method calls
        //(since the request cannot be understood, the service should not be contacted)
        def mock = Mock(ThingService)
        controller.metaClass.getService = {-> mock}

        request.addHeader('Accept','application/json')
        request.addHeader('Content-Type','application/json')
        request.method = httpMethod
        //make sure the body isn't zero length
        //so that the controller has to look at the
        //content-type for delete
        request.setContent( new byte[1] )
        params.pluralizedResourceName = 'things'
        if (id != null) params.id = id

        when:
        controller."$controllerMethod"()

        then:
        415 == response.status
          0 == response.getContentLength()
        0 * _._

        where:
        controllerMethod | httpMethod | id   | serviceMethod | serviceReturn
        'create'         | 'POST'     | null | 'create'      | 'foo'
        'update'         | 'PUT'      | '1'  | 'update'      | 'foo'
        'delete'         | 'DELETE'   | '1'  | 'delete'      | null
    }

    @Unroll
    def "Media type in Content-Type header without extractor returns 415"(String controllerMethod, String httpMethod, String mediaType, String id, String serviceMethod, def serviceReturn, def body ) {
        setup:
        config.restfulApiConfig = {
            resource {
                name = 'things'
                representation {
                    mediaType = 'application/json'
                }
                representation {
                    mediaType = 'application/xml'
                    jsonAsXml = true
                    extractor = new net.hedtech.restfulapi.extractors.xml.JSONObjectExtractor()
                }
                representation {
                    mediaType = 'application/custom-xml'
                }
                representation {
                    mediaType = 'application/custom-thing-json'
                    extractor = new net.hedtech.restfulapi.extractors.json.DefaultJSONExtractor()
                }
                representation {
                    mediaType = 'application/custom-thing-xml'
                    jsonAsXml = true
                }
            }
        }
        controller.init()

        //mock the appropriate service method, but expect no method calls
        //(since the request cannot be understood, the service should not be contacted)
        def mock = Mock(ThingService)
        controller.metaClass.getService = {-> mock}

        request.addHeader( 'Accept', 'application/json' )
        request.addHeader( 'Content-Type', mediaType  )
        //incoming format not a registered media type, so
        //simulate fallback to html
        request.method = httpMethod
        if (body != null) request.setContent( body.getBytes('UTF-8' ) )
        params.pluralizedResourceName = 'things'
        if (id != null) params.id = id

        when:
        controller."$controllerMethod"()

        then:
        415 == response.status
          0 == response.getContentLength()
        0 * _._

        where:
        //test data for the current 3 'buckets' an incoming request falls into:
        //json content, json-as-xml content (xml), and custom xml (any format not)
        //starting with 'xml'
        controllerMethod | httpMethod | mediaType                      | id   | serviceMethod | serviceReturn    | body
        'create'         | 'POST'     | 'application/json'             | null | 'create'      | 'foo'            | null
        'update'         | 'PUT'      | 'application/json'             | '1'  | 'update'      | 'foo'            | null
        'delete'         | 'DELETE'   | 'application/json'             | '1'  | 'delete'      | null             | null
        'create'         | 'POST'     | 'application/xml'              | null | 'create'      | 'foo'            | """<?xml version="1.0" encoding="UTF-8"?><json><code>AC</code><description>An AC thingy</description></json>"""
        'update'         | 'PUT'      | 'application/xml'              | '1'  | 'update'      | 'foo'            | """<?xml version="1.0" encoding="UTF-8"?><json><code>AC</code><description>An AC thingy</description></json>"""
        'delete'         | 'DELETE'   | 'application/xml'              | '1'  | 'delete'      | null             | """<?xml version="1.0" encoding="UTF-8"?><json><code>AC</code><description>An AC thingy</description></json>"""
        'create'         | 'POST'     | 'application/custom-xml'       | null | 'create'      | 'foo'            | null
        'update'         | 'PUT'      | 'application/custom-xml'       | '1'  | 'update'      | 'foo'            | null
        'delete'         | 'DELETE'   | 'application/custom-xml'       | '1'  | 'delete'      | null             | null
        'create'         | 'POST'     | 'application/custom-thing-xml' | null | 'create'      | 'foo'            | null
        'update'         | 'PUT'      | 'application/custom-thing-xml' | '1'  | 'update'      | 'foo'            | null
        'delete'         | 'DELETE'   | 'application/custom-thing-xml' | '1'  | 'delete'      | null             | null
    }

    @Unroll
    def "Media type without extractor in Content-Type header for list and show is ignored"(String controllerMethod, String httpMethod, String id, String serviceMethod, def serviceReturn ) {
        setup:
        config.restfulApiConfig = {
            resource {
                name = 'things'
                representation {
                    mediaType = 'application/json'
                }
            }
        }
        controller.init()
        //mock the appropriate service method, but expect no method calls
        //(since the request cannot be understood, the service should not be contacted)
        def mock = Mock(ThingService)
        controller.metaClass.getService = { -> mock }

        // Since both our show and list methods use a closure from the cache-headers
        // plugin, we need to mock that closure
        def cacheHeadersService = new CacheHeadersService()
        Closure withCacheHeadersClosure = { Closure c ->
            c.delegate = controller
            c.resolveStrategy = Closure.DELEGATE_ONLY
            cacheHeadersService.withCacheHeaders( c.delegate, c )
        }
        controller.metaClass.withCacheHeaders = withCacheHeadersClosure

        request.addHeader('Accept','application/json')
        request.addHeader('Content-Type','application/json')
        //incoming format not a registered media type, so
        //simulate fallback to html
        request.method = httpMethod
        params.pluralizedResourceName = 'things'
        if (id != null) params.id = id
        if (serviceMethod == 'list') {
            1 * mock.count(_) >> {return 5}
        }

        when:
        controller."$controllerMethod"()

        then:
        200 == response.status
          0 < response.text.length()
        1 * mock."$serviceMethod"(_) >> { return serviceReturn }

        where:
        controllerMethod | httpMethod | id   | serviceMethod | serviceReturn
        'list'           | 'GET'      | null | 'list'        | ['foo']
        'show'           | 'GET'      | '1'  | 'show'        | [foo:'foo']
    }

    def "Test the controller validates the configuration"() {
        setup:
        config.restfulApiConfig =
        {
            resource {
                name = 'things'
                representation {
                    mediaType = 'application/xml'
                    jsonAsXml = true
                }
            }
        }

        when:
        controller.init()

        then:
        def e = thrown(MissingJSONEquivalent)
        'things' == e.resourceName
        'application/xml' == e.mediaType
    }

    def "Test that mismatch between id in url and resource representation returns 400"(def controllerMethod, def httpMethod, def id, def body) {
        setup:
        //use default extractor for any methods with a request body
         config.restfulApiConfig = {
            resource {
                name = 'things'
                representation {
                    mediaType = 'application/json'
                    extractor = new DefaultJSONExtractor()
                }
            }
        }
        controller.init()

        def mock = Mock(ThingService)
        controller.metaClass.getService = {-> mock}

        request.addHeader( 'Accept', 'application/json' )
        request.addHeader( 'Content-Type', 'application/json' )
        request.method = httpMethod
        params.pluralizedResourceName = 'things'
        params.id = id
        request.setContent( body.getBytes('UTF-8' ) )

        when:
        controller."$controllerMethod"()

        then:
        400 == response.status
          0 == response.getContentLength()
          0 * _._
        'Id mismatch' == response.getHeaderValue( 'X-Status-Reason' )
        'default.rest.idmismatch.message' == response.getHeaderValue( 'X-hedtech-message' )

        where:
        controllerMethod | httpMethod | id   | body
        'update'         | 'PUT'      | '1'  | '{id:2}'
        'delete'         | 'DELETE'   | '1'  | '{id:2}'
    }

    def "Test that service name can be overridden in configuration"() {
      setup:
        //use default extractor for any methods with a request body
         config.restfulApiConfig = {
            resource {
                name = 'things'
                serviceName = 'theThingService'
                representation {
                    mediaType = 'application/json'
                    extractor = new DefaultJSONExtractor()
                }
            }
        }
        controller.init()
        params.pluralizedResourceName = 'things'

        when:
        def serviceName = controller.getServiceName()

        then:
        'theThingService' == serviceName
    }

    @Unroll
    def "Unsupported method returns 405"(String controllerMethod, def allowedMethods, def allowHeader ) {
        setup:
        config.restfulApiConfig = {
            resource {
                name = 'things'
                methods = allowedMethods
                representation {
                    mediaType = 'application/json'
                }
            }
        }
        controller.init()

        //mock the appropriate service method, but expect no method calls
        //(since the request cannot be understood, the service should not be contacted)
        def mock = Mock(ThingService)
        controller.metaClass.getService = {-> mock}

        request.addHeader( 'Accept', 'application/json' )
        request.addHeader( 'Content-Type', 'application/json' )
        params.pluralizedResourceName = 'things'

        when:
        controller."$controllerMethod"()

        then:
        405          == response.status
          0          == response.getContentLength()
        allowHeader.size()  == response.headers( 'Allow' ).size()
        allowHeader as Set == response.headers( 'Allow') as Set
        0 * _._

        where:
        //test data for the current 3 'buckets' an incoming request falls into:
        //json content, json-as-xml content (xml), and custom xml (any format not)
        //starting with 'xml'
        controllerMethod | allowedMethods                       | allowHeader
        'list'           | ['show','update','delete']           | []
        'list'           | ['create','show','update','delete']  | ["POST"]
        'list'           | []                                   | []
        'list'           | ['create']                           | ["POST"]
        'create'         | ['show','update','delete']           | []
        'create'         | ['list','show','update','delete']    | ["GET"]
        'create'         | []                                   | []
        'create'         | ['list']                             | ["GET"]
        'show'           | []                                   | []
        'show'           | ['update','delete']                  | ["PUT","DELETE"]
        'show'           | ['update','list']                    | ["PUT"]
        'update'         | []                                   | []
        'update'         | ['delete','show']                    | ["DELETE","GET"]
        'update'         | ['show','list','create']             | ["GET"]
        'delete'         | []                                   | []
        'delete'         | ['show','update']                    | ["GET","PUT"]
        'delete'         | ['update','list','create']           | ["PUT"]
        'delete'         | ['update','show', 'list','create']   | ["GET","PUT"]
    }

    def "Test optimistic lock returns 409"() {
        setup:
        //use default extractor for any methods with a request body
         config.restfulApiConfig = {
            resource {
                name = 'things'
                representation {
                    mediaType = 'application/json'
                    extractor = new DefaultJSONExtractor()
                }
            }
        }
        controller.init()

        //mock the appropriate service method, expect exactly 1 invocation
        def mock = Mock(ThingService)
        controller.metaClass.getService = {-> mock}


        request.addHeader( 'Accept', 'application/json' )
        //incoming format always json, so no errors
        request.addHeader( 'Content-Type', 'application/json' )
        params.pluralizedResourceName = 'things'

        when:
        controller.update()

        then:
        1*mock.update(_,_) >> { throw new org.springframework.dao.OptimisticLockingFailureException( "foo" ) }
        409 == response.status
          0 == response.getContentLength()
        'default.optimistic.locking.failure' == response.getHeaderValue( 'X-hedtech-message' )
    }

    def "Test generic error returns 500"() {
        setup:
        //use default extractor for any methods with a request body
         config.restfulApiConfig = {
            resource {
                name = 'things'
                representation {
                    mediaType = 'application/json'
                    extractor = new DefaultJSONExtractor()
                }
            }
        }
        controller.init()

        //mock the appropriate service method, expect exactly 1 invocation
        def mock = Mock(ThingService)
        controller.metaClass.getService = {-> mock}


        request.addHeader( 'Accept', 'application/json' )
        //incoming format always json, so no errors
        request.addHeader( 'Content-Type', 'application/json' )
        params.pluralizedResourceName = 'things'

        when:
        controller.update()

        then:
        1*mock.update(_,_) >> { throw new Exception( 'foo' ) }
        500 == response.status
          0 == response.getContentLength()
        'default.rest.general.errors.message' == response.getHeaderValue( 'X-hedtech-message' )
    }

    def "Test that delete with empty body ignores Content-Type"() {
        setup:
        //use default extractor for any methods with a request body
         config.restfulApiConfig = {
            resource {
                name = 'things'
                representation {
                    mediaType = 'application/json'
                    extractor = new DefaultJSONExtractor()
                }
            }
        }
        controller.init()

        //mock the appropriate service method, expect exactly 1 invocation
        def mock = Mock(ThingService)
        controller.metaClass.getService = {-> mock}

        request.setContent( new byte[0] )
        request.addHeader( 'Accept', 'application/json' )
        request.addHeader( 'Content-Type', 'application/xml' )
        params.pluralizedResourceName = 'things'

        when:
        controller.delete()

        then:
        200 == response.status
          0 == response.getContentLength()
          1*mock.delete(_,_) >> { }
        'default.rest.deleted.message' == response.getHeaderValue( 'X-hedtech-message' )

    }


}
