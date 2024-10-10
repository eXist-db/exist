/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.xquery;

import org.exist.dom.QName;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Type;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * A small DSL which makes defining Functions in Java code
 * much simpler and more readable.
 *
 * It allows you to define functions signatures by using a DSL like:
 *
 * <pre>
 * {@code
 * private static final String FS_INSERT_BEFORE_NAME = "insert-before";
 * static final FunctionSignature FS_INSERT_BEFORE = functionSignature(
 *         FS_INSERT_BEFORE_NAME,
 *         "Returns a specified part of binary data.",
 *         returnsOpt(Type.BASE64_BINARY),
 *         optParam("in", Type.BASE64_BINARY, "The binary data"),
 *         param("offset", Type.INTEGER, "The offset to insert at"),
 *         optParam("extra", Type.BASE64_BINARY, "The binary data to insert")
 * );
 * }
 * </pre>
 *
 * If you have a function, that is "overloaded" with multiple arity
 * applications possible, you can define it by using a DSL like:
 *
 * <pre>
 * {@code
 * private static final String FS_PART_NAME = "part";
 * private static final FunctionParameterSequenceType FS_OPT_PARAM_IN = optParam("in", Type.BASE64_BINARY, "The binary data");
 * private static final FunctionParameterSequenceType FS_PART_PARAM_OFFSET = param("offset", Type.INTEGER, "The offset to start reading from");
 * static final FunctionSignature[] FS_PART = functionSignatures(
 *         FS_PART_NAME,
 *         "Returns a specified part of binary data.",
 *         returnsOpt(Type.BASE64_BINARY),
 *         arities(
 *             arity(
 *                 FS_OPT_PARAM_IN,
 *                 FS_PART_PARAM_OFFSET
 *             ),
 *             arity(
 *                 FS_OPT_PARAM_IN,
 *                 FS_PART_PARAM_OFFSET,
 *                 param("size", Type.INTEGER, "The number of octets to read from the offset")
 *             )
 *         )
 * );
 * }
 * </pre>
 *
 * Finally, registering function definitions in a {@link AbstractInternalModule}
 * can be done by using a DSL like:
 *
 * <pre>
 * {@code
 * public static final FunctionDef[] functions = functionDefs(
 *         functionDefs(ConversionFunctions.class,
 *             ConversionFunctions.FS_HEX,
 *             ConversionFunctions.FS_BIN),
 *
 *         functionDefs(BasicFunctions.class,
 *             BasicFunctions.FS_INSERT_BEFORE,
 *             BasicFunctions.FS_PART[0],
 *             BasicFunctions.FS_PART[1])
 * );
 * }
 * </pre>
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class FunctionDSL {

    /**
     * Convenience DSL method to create a Function Definition
     *
     * @param functionSignature The signature of the function
     * @param clazz The {@link Function} clazz where the function is implemented
     *
     * @return The function definition object
     */
    public static FunctionDef functionDef(final FunctionSignature functionSignature, Class<? extends Function> clazz) {
        return new FunctionDef(functionSignature, clazz);
    }

    /**
     * Convenience DSL method for supplying multiple function definitions using Varargs syntax
     * where the implementations are all within the same {@link Function} class
     *
     * @param clazz The {@link Function} class which holds all the implementations described by {@code functionSignature}
     * @param functionSignatures The signatures which are implemented by {@code clazz}
     *
     * @return The array of function definitions
     */
    public static FunctionDef[] functionDefs(final Class<? extends Function> clazz, final FunctionSignature... functionSignatures) {
        return Arrays.stream(functionSignatures)
                .map(fs -> functionDef(fs, clazz))
                .toArray(FunctionDef[]::new);
    }

    /**
     * Convenience DSL method for merging arrays of functions definitions using Varags syntax
     *
     * @param functionDefss The arrays of function definitions
     *
     * @return An array containing all function definitions supplied in {@code functionDefss}
     */
    public static FunctionDef[] functionDefs(final FunctionDef[]... functionDefss) {
        return Arrays.stream(functionDefss)
                .map(Arrays::stream)
                .reduce(Stream::concat)
                .map(s -> s.toArray(FunctionDef[]::new))
                .orElse(new FunctionDef[0]);
    }

    /**
     * Creates a new Function signature using Varargs syntax
     * for the parameters
     *
     * @param name The name of the function
     * @param description A description of the purpose of the function
     * @param returnType The type that is returned by the function
     * @param paramTypes The (types of) parameters that the function accepts
     *
     * @return The function signature object
     */
    public static FunctionSignature functionSignature(final QName name, final String description,
            final FunctionReturnSequenceType returnType, final FunctionParameterSequenceType... paramTypes) {
        return new FunctionSignature(
                name,
                description,
                paramTypes,
                returnType
        );
    }

    /**
     * Deprecates a function signature
     *
     * @param deprecationDescription An explanation of the purpose for deprecation
     * @param functionSignature The functionSignature to deprecate
     *
     * @return The function signature object
     */
    public static FunctionSignature deprecated(final String deprecationDescription, final FunctionSignature functionSignature) {
        return new FunctionSignature(
                functionSignature.getName(),
                functionSignature.getDescription(),
                functionSignature.getArgumentTypes(),
                functionSignature.getReturnType(),
                deprecationDescription
        );
    }

//    /**
//     * Deprecates a function signature
//     *
//     * @param fsDeprecates The new functionSignature which deprecates <code>functionSignature</code>
//     * @param functionSignature The functionSignature to deprecate
//     *
//     * @return The function signature object
//     */
//    public static FunctionSignature deprecated(final FunctionSignature fsDeprecates, final FunctionSignature functionSignature) {
//        return new FunctionSignature(
//                functionSignature.getName(),
//                functionSignature.getDescription(),
//                functionSignature.getArgumentTypes(),
//                functionSignature.getReturnType(),
//                fsDeprecates
//        );
//    }

    /**
     * Creates multiple Function signatures for functions that have multiple arity definitions
     *
     * The {@code name}, {@code description} and {@code returnType} parameters remain the same for each function arity
     * however the {@code variableParamType} allows you to specify different arguments for each arity definition
     *
     * @param name The name of the functions
     * @param description A description of the purpose of the functions
     * @param returnType The type that is returned by all arities of the function
     * @param variableParamTypes An array, where each entry is an arry of parameter types for a specific arity of the function
     *
     * @return The function signature objects
     */
    public static FunctionSignature[] functionSignatures(final QName name, final String description,
                                                         final FunctionReturnSequenceType returnType, final FunctionParameterSequenceType[][] variableParamTypes) {
        return Arrays.stream(variableParamTypes)
                .map(paramTypes -> functionSignature(name, description, returnType, paramTypes))
                .toArray(FunctionSignature[]::new);
    }

    /**
     * Wraps the parameter types for a specific function arity
     *
     * A DSL convenience method to be used to supply multiple {@link #arity(FunctionParameterSequenceType...)} results
     * to {@link #functionSignatures(QName, String, FunctionReturnSequenceType, FunctionParameterSequenceType[][])}
     *
     * @param variableParamTypes A convenience Varargs for the function signature arities
     *
     * @return The arities of function parameters for a function signature
     */
    public static FunctionParameterSequenceType[][] arities(final FunctionParameterSequenceType[]... variableParamTypes) {
        return variableParamTypes;
    }

    /**
     * Specifies the specific parameter types for an arity of a function signature.
     *
     * A DSL convenience method to be used inside {@link #arities(FunctionParameterSequenceType[][])}.
     *
     * @param paramTypes A convenience Varargs for the parameter types for a function arity
     *
     * @return The parameter types for a function arity
     */
    public static FunctionParameterSequenceType[] arity(final FunctionParameterSequenceType... paramTypes) {
        return paramTypes;
    }

    /**
     * An optional  DSL convenience method for function parameter types
     * that may make the function signature DSL more readable
     *
     * @param paramTypes The parameter types
     *
     * @return The parameter types
     */
    public static FunctionParameterSequenceType[] params(final FunctionParameterSequenceType... paramTypes) {
        return paramTypes;
    }

    /**
     * Creates a Function Parameter which has a cardinality of {@link Cardinality#ZERO_OR_ONE}
     *
     * @param name The name of the parameter
     * @param type The XDM type of the parameter, i.e. one of {@link org.exist.xquery.value.Type}
     * @param description A description of the parameter
     *
     * @return The function parameter object
     */
    public static FunctionParameterSequenceType optParam(final String name, final int type, final String description) {
        return param(name, type, Cardinality.ZERO_OR_ONE, description);
    }

    /**
     * Creates a Function Parameter which has a cardinality of {@link Cardinality#EXACTLY_ONE}
     *
     * @param name The name of the parameter
     * @param type The XDM type of the parameter, i.e. one of {@link org.exist.xquery.value.Type}
     * @param description A description of the parameter
     *
     * @return The function parameter object
     */
    public static FunctionParameterSequenceType param(final String name, final int type, final String description) {
        return param(name, type, Cardinality.EXACTLY_ONE, description);
    }

    /**
     * Creates a Function Parameter which has a cardinality of {@link Cardinality#ONE_OR_MORE}
     *
     * @param name The name of the parameter
     * @param type The XDM type of the parameter, i.e. one of {@link org.exist.xquery.value.Type}
     * @param description A description of the parameter
     *
     * @return The function parameter object
     */
    public static FunctionParameterSequenceType manyParam(final String name, final int type, final String description) {
        return param(name, type, Cardinality.ONE_OR_MORE, description);
    }

    /**
     * Creates a Function Parameter which has a cardinality of {@link Cardinality#ZERO_OR_ONE}
     *
     * @param name The name of the parameter
     * @param type The XDM type of the parameter, i.e. one of {@link org.exist.xquery.value.Type}
     * @param description A description of the parameter
     *
     * @return The function parameter object
     */
    public static FunctionParameterSequenceType optManyParam(final String name, final int type,
            final String description) {
        return param(name, type, Cardinality.ZERO_OR_MORE, description);
    }

    /**
     * Creates a Function Parameter
     *
     * @param name The name of the parameter
     * @param type The XDM type of the parameter, i.e. one of {@link org.exist.xquery.value.Type}
     * @param cardinality The cardinality of the parameter, i.e. one of {@link Cardinality}
     * @param description A description of the parameter
     *
     * @return The function parameter object
     */
    public static FunctionParameterSequenceType param(final String name, final int type, final Cardinality cardinality,
            final String description) {
        return new FunctionParameterSequenceType(name, type, cardinality, description);
    }

    /**
     * Creates a Function Parameter
     *
     * @param name The name of the parameter
     * @param type The XDM type of the parameter, i.e. one of {@link org.exist.xquery.value.Type}
     * @param cardinality The cardinality of the parameter
     * @param description A description of the parameter
     *
     * @return The function parameter object
     *
     * @deprecated Use {@link #param(String, int, Cardinality, String)}
     */
    @Deprecated
    public static FunctionParameterSequenceType param(final String name, final int type, final int cardinality,
            final String description) {
        return new FunctionParameterSequenceType(name, type, cardinality, description);
    }

    /**
     * Creates a Function Return Type which has a cardinality of {@link Cardinality#ZERO_OR_ONE}
     *
     * @param type The XDM type of the return value, i.e. one of {@link org.exist.xquery.value.Type}
     *
     * @return The function return type object
     */
    public static FunctionReturnSequenceType returnsOpt(final int type) {
        return returns(type, Cardinality.ZERO_OR_ONE);
    }

    /**
     * Creates a Function Return Type which has a cardinality of {@link Cardinality#ZERO_OR_ONE}
     *
     * @param type The XDM type of the return value, i.e. one of {@link org.exist.xquery.value.Type}
     * @param description A description of the return value
     *
     * @return The function return type object
     */
    public static FunctionReturnSequenceType returnsOpt(final int type, final String description) {
        return returns(type, Cardinality.ZERO_OR_ONE, description);
    }

    /**
     * Creates a Function Return Type which has a cardinality of {@link Cardinality#EXACTLY_ONE}
     *
     * @param type The XDM type of the return value, i.e. one of {@link org.exist.xquery.value.Type}
     *
     * @return The function return type object
     */
    public static FunctionReturnSequenceType returns(final int type) {
        return returns(type, Cardinality.EXACTLY_ONE);
    }

    /**
     * Creates a Function Return Type which has a cardinality of {@link Cardinality#EXACTLY_ONE}
     *
     * @param type The XDM type of the return value, i.e. one of {@link org.exist.xquery.value.Type}
     * @param description A description of the return value
     *
     * @return The function return type object
     */
    public static FunctionReturnSequenceType returns(final int type, final String description) {
        return returns(type, Cardinality.EXACTLY_ONE, description);
    }

    /**
     * Creates a Function Return Type which has a cardinality of {@link Cardinality#ONE_OR_MORE}
     *
     * @param type The XDM type of the return value, i.e. one of {@link org.exist.xquery.value.Type}
     *
     * @return The function return type object
     */
    public static FunctionReturnSequenceType returnsMany(final int type) {
        return returns(type, Cardinality.ONE_OR_MORE);
    }

    /**
     * Creates a Function Return Type which has a cardinality of {@link Cardinality#ONE_OR_MORE}
     *
     * @param type The XDM type of the return value, i.e. one of {@link org.exist.xquery.value.Type}
     * @param description A description of the return value
     *
     * @return The function return type object
     */
    public static FunctionReturnSequenceType returnsMany(final int type, final String description) {
        return returns(type, Cardinality.ONE_OR_MORE, description);
    }

    /**
     * Creates a Function Return Type which has a cardinality of {@link Cardinality#ZERO_OR_MORE}
     *
     * @param type The XDM type of the return value, i.e. one of {@link org.exist.xquery.value.Type}
     *
     * @return The function return type object
     */
    public static FunctionReturnSequenceType returnsOptMany(final int type) {
        return returns(type, Cardinality.ZERO_OR_MORE);
    }

    /**
     * Creates a Function Return Type which has a cardinality of {@link Cardinality#ZERO_OR_MORE}
     *
     * @param type The XDM type of the return value, i.e. one of {@link org.exist.xquery.value.Type}
     * @param description A description of the return value
     *
     * @return The function return type object
     */
    public static FunctionReturnSequenceType returnsOptMany(final int type, final String description) {
        return returns(type, Cardinality.ZERO_OR_MORE, description);
    }

    /**
     * Creates a Function Return Type which describes no result.
     *
     * @return a Function Return Type which has a cardinality of {@link Cardinality#EMPTY_SEQUENCE} and {@link Type#EMPTY_SEQUENCE}
     */
    public static FunctionReturnSequenceType returnsNothing() {
        return new FunctionReturnSequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE, null);
    }

    /**
     * Creates a Function Return Type
     *
     * @param type The XDM type of the return value, i.e. one of {@link org.exist.xquery.value.Type}
     * @param cardinality The cardinality of the return type, i.e. one of {@link Cardinality}
     *
     * @return The function return type object
     */
    public static FunctionReturnSequenceType returns(final int type, final Cardinality cardinality) {
        return returns(type, cardinality, null);
    }

    /**
     * Creates a Function Return Type
     *
     * @param type The XDM type of the return value, i.e. one of {@link org.exist.xquery.value.Type}
     * @param cardinality The cardinality of the return type
     *
     * @return The function return type object
     *
     * @deprecated Use {@link #returns(int, Cardinality)}
     */
    @Deprecated
    public static FunctionReturnSequenceType returns(final int type, final int cardinality) {
        return returns(type, cardinality, null);
    }

    /**
     * Creates a Function Return Type
     *
     * @param type The XDM type of the return value, i.e. one of {@link org.exist.xquery.value.Type}
     * @param cardinality The cardinality of the return type, i.e. one of {@link Cardinality}
     * @param description A description of the parameter
     *
     * @return The function return type object
     */
    public static FunctionReturnSequenceType returns(final int type, final Cardinality cardinality, final String description) {
        return new FunctionReturnSequenceType(type, cardinality, description);
    }

    /**
     * Creates a Function Return Type
     *
     * @param type The XDM type of the return value, i.e. one of {@link org.exist.xquery.value.Type}
     * @param cardinality The cardinality of the return type
     * @param description A description of the parameter
     *
     * @return The function return type object
     *
     * @deprecated Use {@link #returns(int, Cardinality, String)}
     */
    @Deprecated
    public static FunctionReturnSequenceType returns(final int type, final int cardinality, final String description) {
        return new FunctionReturnSequenceType(type, cardinality, description);
    }
}
