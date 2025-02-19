/*
 * Licensed to Crate.io GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.expression;

import java.util.List;
import java.util.Locale;

import io.crate.common.collections.Lists2;
import io.crate.data.Input;
import io.crate.exceptions.UnsupportedFeatureException;
import io.crate.expression.symbol.AliasSymbol;
import io.crate.expression.symbol.DynamicReference;
import io.crate.expression.symbol.Function;
import io.crate.expression.symbol.Literal;
import io.crate.expression.symbol.Symbol;
import io.crate.expression.symbol.SymbolVisitor;
import io.crate.expression.symbol.VoidReference;
import io.crate.metadata.FunctionImplementation;
import io.crate.metadata.NodeContext;
import io.crate.metadata.Scalar;
import io.crate.metadata.TransactionContext;
import io.crate.metadata.functions.Signature;

public class BaseImplementationSymbolVisitor<C> extends SymbolVisitor<C, Input<?>> {

    protected final TransactionContext txnCtx;
    protected final NodeContext nodeCtx;

    public BaseImplementationSymbolVisitor(TransactionContext txnCtx, NodeContext nodeCtx) {
        this.txnCtx = txnCtx;
        this.nodeCtx = nodeCtx;
    }

    @Override
    public Input<?> visitFunction(Function function, C context) {
        Signature signature = function.signature();
        FunctionImplementation functionImplementation = nodeCtx.functions().getQualified(function);
        assert functionImplementation != null : "Function implementation not found using full qualified lookup";

        if (functionImplementation instanceof Scalar<?, ?>) {
            List<Symbol> arguments = function.arguments();
            Scalar<?, ?> scalarImpl = ((Scalar) functionImplementation).compile(arguments, txnCtx.sessionSettings().userName(), nodeCtx.roles());
            Input[] argumentInputs = new Input[arguments.size()];
            int i = 0;
            for (Symbol argument : function.arguments()) {
                argumentInputs[i++] = argument.accept(this, context);
            }
            return new FunctionExpression<>(txnCtx, nodeCtx, scalarImpl, argumentInputs);
        } else {
            throw new UnsupportedFeatureException(
                String.format(
                    Locale.ENGLISH,
                    "Function %s(%s) is not a scalar function.",
                    signature.getName(),
                    Lists2.joinOn(", ", function.arguments(), x -> x.valueType().getName())
                )
            );
        }
    }

    @Override
    public Input<?> visitLiteral(Literal<?> symbol, C context) {
        return symbol;
    }

    @Override
    public Input<?> visitDynamicReference(DynamicReference symbol, C context) {
        return visitReference(symbol, context);
    }

    @Override
    public Input<?> visitAlias(AliasSymbol aliasSymbol, C context) {
        return aliasSymbol.symbol().accept(this, context);
    }

    @Override
    public Input<?> visitVoidReference(VoidReference symbol, C context) {
        return visitReference(symbol, context);
    }

    @Override
    protected Input<?> visitSymbol(Symbol symbol, C context) {
        throw new UnsupportedOperationException(
            String.format(Locale.ENGLISH, "Can't handle Symbol [%s: %s]", symbol.getClass().getSimpleName(), symbol));
    }
}
