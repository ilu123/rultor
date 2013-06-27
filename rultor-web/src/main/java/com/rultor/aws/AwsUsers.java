/**
 * Copyright (c) 2009-2013, rultor.com
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
package com.rultor.aws;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.jcabi.aspects.Immutable;
import com.jcabi.aspects.Loggable;
import com.jcabi.dynamo.Item;
import com.jcabi.dynamo.Region;
import com.jcabi.urn.URN;
import com.rultor.spi.Metricable;
import com.rultor.spi.Repo;
import com.rultor.spi.User;
import com.rultor.spi.Users;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * All users in Dynamo DB.
 *
 * @author Yegor Bugayenko (yegor@tpc2.com)
 * @version $Id$
 * @since 1.0
 */
@Immutable
@ToString
@EqualsAndHashCode(of = "region")
@Loggable(Loggable.DEBUG)
public final class AwsUsers implements Users, Metricable {

    /**
     * Dynamo.
     */
    private final transient Region region;

    /**
     * Repo to create drains.
     */
    private final transient Repo repo;

    /**
     * Public ctor.
     * @param reg AWS region
     * @param rep Repo
     */
    public AwsUsers(final Region reg, final Repo rep) {
        this.region = reg;
        this.repo = rep;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<URN, User> everybody() {
        final ConcurrentMap<URN, User> users =
            new ConcurrentSkipListMap<URN, User>();
        for (Item item : this.region.table("units").frame()) {
            final URN urn = URN.create(item.get(AwsUnit.KEY_OWNER).getS());
            if (!users.containsKey(urn)) {
                users.put(urn, this.fetch(urn));
            }
        }
        return Collections.unmodifiableMap(users);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void register(final MetricRegistry registry) {
        registry.register(
            MetricRegistry.name(this.getClass(), "users-total"),
            new Gauge<Integer>() {
                @Override
                public Integer getValue() {
                    return AwsUsers.this.everybody().size();
                }
            }
        );
        Caches.INSTANCE.register(registry);
    }

    /**
     * Make user by URN.
     * @param urn The URN
     * @return The user
     */
    private User fetch(final URN urn) {
        return new AwsUser(this.region, this.repo, urn);
    }

}
