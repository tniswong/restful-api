/* ****************************************************************************
 * Copyright 2013 Ellucian Company L.P. and its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *****************************************************************************/

package net.hedtech.restfulapi.config

import grails.test.mixin.*
import grails.test.mixin.support.*

import net.hedtech.restfulapi.*
import net.hedtech.restfulapi.beans.*
import net.hedtech.restfulapi.extractors.configuration.*
import net.hedtech.restfulapi.extractors.json.*
import net.hedtech.restfulapi.marshallers.json.*

import spock.lang.*


@TestMixin(GrailsUnitTestMixin)
class JSONBeanMarshallerConfigSpec extends Specification {

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
        true          == config.useIncludedFields
    }

    def "Test including no fields"() {
        setup:
        def src = {
            includesFields {
            }
        }

        when:
        def config = invoke( src )

        then:
        []   == config.includedFields
        true == config.useIncludedFields
    }

    def "Test requires included fields"() {
        setup:
        def src = {
            includesFields {
                requiresIncludedFields true
            }
        }

        when:
        def config = invoke( src )

        then:
        true == config.requireIncludedFields
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
        JSONBeanMarshallerConfig one = new JSONBeanMarshallerConfig(
            supportClass:SimpleBean,
            fieldNames:['foo':'foo1','bar':'bar1'],
            includedFields:['foo','bar'],
            useIncludedFields:true,
            excludedFields:['e1','e2'],
            additionalFieldClosures:[{app,bean,json ->}],
            additionalFieldsMap:['one':'one','two':'two'],
            requireIncludedFields:true
        )
        JSONBeanMarshallerConfig two = new JSONBeanMarshallerConfig(
            supportClass:Thing,
            fieldNames:['foo':'foo2','baz':'baz1'],
            includedFields:['baz'],
            useIncludedFields:false,
            excludedFields:['e3'],
            additionalFieldClosures:[{app,bean,json ->}],
            additionalFieldsMap:['two':'2','three':'3'],
            requireIncludedFields:false
        )

        when:
        def config = one.merge(two)

        then:
        true                                     == config.isSupportClassSet
        Thing                                    == config.supportClass
        ['foo':'foo2','bar':'bar1','baz':'baz1'] == config.fieldNames
        ['foo','bar','baz']                      == config.includedFields
        true                                     == config.useIncludedFields
        ['e1','e2','e3']                         == config.excludedFields
        2                                        == config.additionalFieldClosures.size()
        ['one':'one',"two":'2','three':'3']      == config.additionalFieldsMap
        false                                    == config.requireIncludedFields
    }

    def "Test merging configurations does not alter either object"() {
        setup:
        def c1 = { Map m -> }
        def c2 = { Map m -> }
        JSONBeanMarshallerConfig one = new JSONBeanMarshallerConfig(
            supportClass:Thing,
            fieldNames:['foo':'foo1','bar':'bar1'],
            includedFields:['foo','bar'],
            useIncludedFields:true,
            excludedFields:['e1','e2'],
            additionalFieldClosures:[{app,bean,json ->}],
            additionalFieldsMap:['one':'1'],
            requireIncludedFields:true
        )
        JSONBeanMarshallerConfig two = new JSONBeanMarshallerConfig(
            supportClass:PartOfThing,
            fieldNames:['foo':'foo2','baz':'baz1'],
            includedFields:['baz'],
            useIncludedFields:false,
            excludedFields:['e3'],
            additionalFieldClosures:[{app,bean,json ->}],
            additionalFieldsMap:['two':'2'],
            requireIncludedFields:false
        )

        when:
        one.merge(two)

        then:
        true                        == one.isSupportClassSet
        ['foo':'foo1','bar':'bar1'] == one.fieldNames
        ['foo','bar']               == one.includedFields
        true                        == one.useIncludedFields
        ['e1','e2']                 == one.excludedFields
        1                           == one.additionalFieldClosures.size()
        ['one':'1']                 == one.additionalFieldsMap
        true                        == one.requireIncludedFields

        true                        == two.isSupportClassSet
        ['foo':'foo2','baz':'baz1'] == two.fieldNames
        ['baz']                     == two.includedFields
        false                       == two.useIncludedFields
        ['e3']                      == two.excludedFields
        1                           == two.additionalFieldClosures.size()
        ['two':'2']                 == two.additionalFieldsMap
        false                       == two.requireIncludedFields
    }

    def "Test merging with support class set only on the left"() {
        setup:
        JSONBeanMarshallerConfig one = new JSONBeanMarshallerConfig(
            supportClass:SimpleBean
        )
        JSONBeanMarshallerConfig two = new JSONBeanMarshallerConfig(
        )

        when:
        def config = one.merge(two)

        then:
        SimpleBean == config.supportClass
        true       == config.isSupportClassSet
    }

    def "Test merging marshaller with require included fields set only on the left"() {
        setup:
        JSONBeanMarshallerConfig one = new JSONBeanMarshallerConfig(
            requireIncludedFields:true
        )
        JSONBeanMarshallerConfig two = new JSONBeanMarshallerConfig(
        )

        when:
        def config = one.merge(two)

        then:
        true == config.requireIncludedFields
    }

    def "Test merging marshaller with useIncludedFields set only on the left"() {
        setup:
        JSONBeanMarshallerConfig one = new JSONBeanMarshallerConfig(
            useIncludedFields:true
        )
        JSONBeanMarshallerConfig two = new JSONBeanMarshallerConfig(
        )

        when:
        def config = one.merge(two)

        then:
        true == config.useIncludedFields
    }


    def "Test resolution of marshaller configuration inherits"() {
        setup:
        JSONBeanMarshallerConfig part1 = new JSONBeanMarshallerConfig(
        )
        JSONBeanMarshallerConfig part2 = new JSONBeanMarshallerConfig(
        )
        JSONBeanMarshallerConfig part3 = new JSONBeanMarshallerConfig(
        )
        JSONBeanMarshallerConfig combined = new JSONBeanMarshallerConfig(
            inherits:['part1','part2']
        )
        JSONBeanMarshallerConfig actual = new JSONBeanMarshallerConfig(
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
        JSONBeanMarshallerConfig part1 = new JSONBeanMarshallerConfig(
            fieldNames:['1':'part1','2':'part1','3':'part1']
        )
        JSONBeanMarshallerConfig part2 = new JSONBeanMarshallerConfig(
            fieldNames:['2':'part2','3':'part2']

        )
        JSONBeanMarshallerConfig actual = new JSONBeanMarshallerConfig(
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

    private JSONBeanMarshallerConfig invoke( Closure c ) {
        JSONBeanMarshallerDelegate delegate = new JSONBeanMarshallerDelegate()
        c.delegate = delegate
        c.resolveStrategy = Closure.DELEGATE_FIRST
        c.call()
        delegate.config
    }
}
