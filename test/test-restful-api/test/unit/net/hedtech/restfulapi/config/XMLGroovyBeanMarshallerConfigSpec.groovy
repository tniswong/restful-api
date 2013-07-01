/* ****************************************************************************
Copyright 2013 Ellucian Company L.P. and its affiliates.
******************************************************************************/

package net.hedtech.restfulapi.config

import grails.test.mixin.*
import spock.lang.*

import net.hedtech.restfulapi.extractors.configuration.*
import net.hedtech.restfulapi.extractors.xml.*
import net.hedtech.restfulapi.*
import net.hedtech.restfulapi.beans.*
import grails.test.mixin.support.*
import net.hedtech.restfulapi.marshallers.xml.*

@TestMixin(GrailsUnitTestMixin)
class XMLGroovyBeanMarshallerConfigSpec extends Specification {

    def "Test inherits"() {
        setup:
        def src = {
            inherits = ['one','two']
        }

        when:
        def config = invoke( src )

        then:
        ['one','two'] == config.inherits

    }

    def "Test priority"() {
        setup:
        def src = {
            priority = 50
        }

        when:
        def config = invoke( src )

        then:
        50 == config.priority
    }

    def "Test supportClass"() {
        setup:
        def src = {
            supports String
        }

        when:
        def config = invoke( src )

        then:
        String == config.supportClass
        true   == config.isSupportClassSet
    }

    def "Test substitutions for field names"() {
        setup:
        def src = {
            field 'one' name 'modOne'
            field 'two' name 'modTwo'
            includesFields {
                field 'three' name 'modThree'
                field 'four' name 'modFour'
            }
        }

        when:
        def config = invoke( src )

        then:
        ['one':'modOne','two':'modTwo','three':'modThree','four':'modFour'] == config.fieldNames
    }

    def "Test included fields"() {
        setup:
        def src = {
            includesFields {
                field 'one'
                field 'two'
            }
        }

        when:
        def config = invoke( src )

        then:
        ['one','two'] == config.includedFields
    }

    def "Test excluded fields"() {
        setup:
        def src = {
            excludesFields {
                field 'one'
                field 'two'
            }
        }

        when:
        def config = invoke( src )

        then:
        ['one','two'] == config.excludedFields
    }

    def "Test additional field closures"() {
        setup:
        def storage = []
        def src = {
            additionalFields {
                Map m -> storage.add 'one'
            }
            additionalFields {
                Map m -> storage.add 'two'
            }
        }

        when:
        def config = invoke( src )
        config.additionalFieldClosures.each {
            it.call([:])
        }

        then:
        2             == config.additionalFieldClosures.size()
        ['one','two'] == storage
    }

    def "Test additionalFieldsMap"() {
        setup:
        def src = {
            additionalFieldsMap = ['one':'one','two':'two']
        }

        when:
        def config = invoke( src )

        then:
        [one:'one',two:'two'] == config.additionalFieldsMap
    }

    def "Test merging configurations"() {
        setup:
        def c1 = { Map m -> }
        def c2 = { Map m -> }
        XMLGroovyBeanMarshallerConfig one = new XMLGroovyBeanMarshallerConfig(
            supportClass:SimpleBean,
            fieldNames:['foo':'foo1','bar':'bar1'],
            includedFields:['foo','bar'],
            excludedFields:['e1','e2'],
            additionalFieldClosures:[{app,bean,xml ->}],
            additionalFieldsMap:['one':'one','two':'two']
        )
        XMLGroovyBeanMarshallerConfig two = new XMLGroovyBeanMarshallerConfig(
            supportClass:Thing,
            fieldNames:['foo':'foo2','baz':'baz1'],
            includedFields:['baz'],
            excludedFields:['e3'],
            additionalFieldClosures:[{app,bean,xml ->}],
            additionalFieldsMap:['two':'2','three':'3']
        )

        when:
        def config = one.merge(two)

        then:
        true                                     == one.isSupportClassSet
        true                                     == two.isSupportClassSet
        Thing                                    == two.supportClass
        ['foo':'foo2','bar':'bar1','baz':'baz1'] == config.fieldNames
        ['foo','bar','baz']                      == config.includedFields
        ['e1','e2','e3']                         == config.excludedFields
        2                                        == config.additionalFieldClosures.size()
        ['one':'one',"two":'2','three':'3']      == config.additionalFieldsMap
    }

    def "Test merging configurations does not alter either object"() {
        setup:
        def c1 = { Map m -> }
        def c2 = { Map m -> }
        XMLGroovyBeanMarshallerConfig one = new XMLGroovyBeanMarshallerConfig(
            supportClass:Thing,
            fieldNames:['foo':'foo1','bar':'bar1'],
            includedFields:['foo','bar'],
            excludedFields:['e1','e2'],
            additionalFieldClosures:[{app,bean,xml ->}],
            additionalFieldsMap:['one':'1']
        )
        XMLGroovyBeanMarshallerConfig two = new XMLGroovyBeanMarshallerConfig(
            supportClass:PartOfThing,
            fieldNames:['foo':'foo2','baz':'baz1'],
            includedFields:['baz'],
            excludedFields:['e3'],
            additionalFieldClosures:[{app,bean,xml ->}],
            additionalFieldsMap:['two':'2']
        )

        when:
        one.merge(two)

        then:
        true                        == one.isSupportClassSet
        ['foo':'foo1','bar':'bar1'] == one.fieldNames
        ['foo','bar']               == one.includedFields
        ['e1','e2']                 == one.excludedFields
        1                           == one.additionalFieldClosures.size()
        ['one':'1']                 == one.additionalFieldsMap

        true                        == two.isSupportClassSet
        ['foo':'foo2','baz':'baz1'] == two.fieldNames
        ['baz']                     == two.includedFields
        ['e3']                      == two.excludedFields
        1                           == two.additionalFieldClosures.size()
        ['two':'2']                 == two.additionalFieldsMap
    }

    def "Test merging with support class set only on the left"() {
        setup:
        def c1 = { Map m -> }
        def c2 = { Map m -> }
        XMLGroovyBeanMarshallerConfig one = new XMLGroovyBeanMarshallerConfig(
            supportClass:SimpleBean
        )
        XMLGroovyBeanMarshallerConfig two = new XMLGroovyBeanMarshallerConfig(
        )

        when:
        def config = one.merge(two)

        then:
        SimpleBean == config.supportClass
        true       == config.isSupportClassSet
    }

    def "Test resolution of domain marshaller configuration inherits"() {
        setup:
        XMLGroovyBeanMarshallerConfig part1 = new XMLGroovyBeanMarshallerConfig(
        )
        XMLGroovyBeanMarshallerConfig part2 = new XMLGroovyBeanMarshallerConfig(
        )
        XMLGroovyBeanMarshallerConfig part3 = new XMLGroovyBeanMarshallerConfig(
        )
        XMLGroovyBeanMarshallerConfig combined = new XMLGroovyBeanMarshallerConfig(
            inherits:['part1','part2']
        )
        XMLGroovyBeanMarshallerConfig actual = new XMLGroovyBeanMarshallerConfig(
            inherits:['combined','part3']
        )
        ConfigGroup group = new ConfigGroup()
        group.configs = ['part1':part1,'part2':part2,'part3':part3,'combined':combined]

        when:
        def resolvedList = group.resolveInherited( actual )

        then:
        [part1,part2,combined,part3,actual] == resolvedList
    }

    def "Test merge order of configuration inherits"() {
        setup:
        XMLGroovyBeanMarshallerConfig part1 = new XMLGroovyBeanMarshallerConfig(
            fieldNames:['1':'part1','2':'part1','3':'part1']
        )
        XMLGroovyBeanMarshallerConfig part2 = new XMLGroovyBeanMarshallerConfig(
            fieldNames:['2':'part2','3':'part2']

        )
        XMLGroovyBeanMarshallerConfig actual = new XMLGroovyBeanMarshallerConfig(
            inherits:['part1','part2'],
            fieldNames:['3':'actual']
        )
        ConfigGroup group = new ConfigGroup()
        group.configs = ['part1':part1,'part2':part2]

        when:
        def config = group.getMergedConfig( actual )

        then:
        ['1':'part1','2':'part2','3':'actual'] == config.fieldNames
    }

    def "Test repeated field clears previous settings"() {
        setup:
        def src = {
            field 'one' name 'modOne'
            field 'one'
            field 'two' name 'modTwo'
            includesFields {
                field 'two'
            }
        }

        when:
        def config = invoke( src )

        then:
        [:] == config.fieldNames
    }

    private XMLGroovyBeanMarshallerConfig invoke( Closure c ) {
        XMLGroovyBeanMarshallerDelegate delegate = new XMLGroovyBeanMarshallerDelegate()
        c.delegate = delegate
        c.resolveStrategy = Closure.DELEGATE_FIRST
        c.call()
        delegate.config
    }
}