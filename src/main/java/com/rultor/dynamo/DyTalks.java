/**
 * Copyright (c) 2009-2014, rultor.com
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met: 1) Redistributions of source code must retain the above
 * copyright notice, this list of conditions and the following
 * disclaimer. 2) Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided
 * with the distribution. 3) Neither the name of the rultor.com nor
 * the names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT
 * NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.rultor.dynamo;

import co.stateful.Counter;
import com.amazonaws.services.dynamodbv2.model.Select;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.jcabi.aspects.Immutable;
import com.jcabi.dynamo.Attributes;
import com.jcabi.dynamo.Conditions;
import com.jcabi.dynamo.Item;
import com.jcabi.dynamo.QueryValve;
import com.jcabi.dynamo.Region;
import com.rultor.spi.Talk;
import com.rultor.spi.Talks;
import java.io.IOException;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Talks in Dynamo.
 *
 * @author Yegor Bugayenko (yegor@tpc2.com)
 * @version $Id$
 * @since 1.0
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@Immutable
@ToString
@EqualsAndHashCode(of = "region")
public final class DyTalks implements Talks {

    /**
     * Table name.
     */
    public static final String TBL = "talks";

    /**
     * Index name.
     * @checkstyle MultipleStringLiteralsCheck (3 lines)
     */
    public static final String IDX_ACTIVE = "active";

    /**
     * Index name.
     * @since 1.3
     */
    public static final String IDX_NUMBERS = "numbers";

    /**
     * Index name.
     * @since 1.9
     */
    public static final String IDX_RECENT = "recent";

    /**
     * Talk unique name.
     */
    public static final String HASH = "name";

    /**
     * Its number.
     * @since 1.3
     */
    public static final String ATTR_NUMBER = "number";

    /**
     * Is it active (1) or archived (0).
     */
    public static final String ATTR_ACTIVE = "active";

    /**
     * XML of the talk.
     */
    public static final String ATTR_XML = "xml";

    /**
     * When updated.
     */
    public static final String ATTR_UPDATED = "updated";

    /**
     * Region we're in.
     */
    private final transient Region region;

    /**
     * Counter of talks.
     */
    private final transient Counter counter;

    /**
     * Public ctor.
     * @param reg Region
     * @param cnt Counter of talks
     */
    public DyTalks(final Region reg, final Counter cnt) {
        this.region = reg;
        this.counter = cnt;
    }

    @Override
    public boolean exists(final long number) {
        return this.region.table(DyTalks.TBL)
            .frame()
            .through(
                new QueryValve()
                    .withLimit(1)
                    .withIndexName(DyTalks.IDX_NUMBERS)
                    .withConsistentRead(false)
            )
            .where(DyTalks.ATTR_NUMBER, Conditions.equalTo(number))
            .iterator().hasNext();
    }

    @Override
    public Talk get(final long number) {
        return new DyTalk(
            this.region.table(DyTalks.TBL)
                .frame()
                .through(
                    new QueryValve()
                        .withLimit(1)
                        .withIndexName(DyTalks.IDX_NUMBERS)
                        .withConsistentRead(false)
                        .withSelect(Select.SPECIFIC_ATTRIBUTES)
                        .withAttributesToGet(DyTalks.HASH, DyTalks.ATTR_NUMBER)
                )
                .where(DyTalks.ATTR_NUMBER, Conditions.equalTo(number))
                .iterator().next()
        );
    }

    @Override
    public boolean exists(final String name) {
        return this.region.table(DyTalks.TBL)
            .frame()
            .through(new QueryValve().withLimit(1))
            .where(DyTalks.HASH, name)
            .iterator().hasNext();
    }

    @Override
    public Talk get(final String name) {
        return new DyTalk(
            this.region.table(DyTalks.TBL)
                .frame()
                .through(
                    new QueryValve()
                        .withLimit(1)
                        .withAttributesToGet(DyTalks.ATTR_NUMBER)
                )
                .where(DyTalks.HASH, name)
                .iterator().next()
        );
    }

    @Override
    public void create(final String name) throws IOException {
        final long number = this.counter.incrementAndGet(1L);
        this.region.table(DyTalks.TBL).put(
            new Attributes()
                .with(DyTalks.HASH, name)
                .with(DyTalks.ATTR_ACTIVE, Boolean.toString(true))
                .with(DyTalks.ATTR_NUMBER, number)
                .with(DyTalks.ATTR_UPDATED, System.currentTimeMillis())
                .with(
                    DyTalks.ATTR_XML,
                    String.format("<talk name='%s' number='%d'/>", name, number)
                )
        );
    }

    @Override
    public Iterable<Talk> active() {
        return Iterables.transform(
            this.region.table(DyTalks.TBL)
                .frame()
                .through(
                    new QueryValve()
                        .withIndexName(DyTalks.IDX_ACTIVE)
                        .withConsistentRead(false)
                        .withSelect(Select.SPECIFIC_ATTRIBUTES)
                        .withAttributesToGet(DyTalks.HASH, DyTalks.ATTR_NUMBER)
                )
                .where(DyTalks.ATTR_ACTIVE, Boolean.toString(true)),
            new Function<Item, Talk>() {
                @Override
                public Talk apply(final Item input) {
                    return new DyTalk(input);
                }
            }
        );
    }

    @Override
    public Iterable<Talk> recent() {
        return Iterables.transform(
            this.region.table(DyTalks.TBL)
                .frame()
                .through(
                    new QueryValve()
                        .withIndexName(DyTalks.IDX_RECENT)
                        .withScanIndexForward(false)
                        .withConsistentRead(false)
                        .withSelect(Select.ALL_PROJECTED_ATTRIBUTES)
                )
                .where(DyTalks.ATTR_ACTIVE, Boolean.toString(false)),
            new Function<Item, Talk>() {
                @Override
                public Talk apply(final Item input) {
                    return new DyTalk(input);
                }
            }
        );
    }
}
