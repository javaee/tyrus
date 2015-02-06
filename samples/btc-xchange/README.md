<!--

    DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.

    Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved.

    The contents of this file are subject to the terms of either the GNU
    General Public License Version 2 only ("GPL") or the Common Development
    and Distribution License("CDDL") (collectively, the "License").  You
    may not use this file except in compliance with the License.  You can
    obtain a copy of the License at
    http://glassfish.java.net/public/CDDL+GPL_1_1.html
    or packager/legal/LICENSE.txt.  See the License for the specific
    language governing permissions and limitations under the License.

    When distributing the software, include this License Header Notice in each
    file and include the License file at packager/legal/LICENSE.txt.

    GPL Classpath Exception:
    Oracle designates this particular file as subject to the "Classpath"
    exception as provided by Oracle in the GPL Version 2 section of the License
    file that accompanied this code.

    Modifications:
    If applicable, add the following below the License Header, with the fields
    enclosed by brackets [] replaced by your own identifying information:
    "Portions Copyright [year] [name of copyright owner]"

    Contributor(s):
    If you wish your version of this file to be governed by only the CDDL or
    only the GPL Version 2, indicate your decision by adding "[Contributor]
    elects to include this software in this distribution under the [CDDL or GPL
    Version 2] license."  If you don't indicate a single choice of license, a
    recipient has the option to distribute your version of this file under
    either the CDDL, the GPL Version 2 or to extend the choice of license to
    its licensees as provided above.  However, if you add GPL Version 2 code
    and therefore, elected the GPL Version 2 license, then the option applies
    only if the new code is made subject to such option by the copyright
    holder.

-->

# Bitcoin market

### Application description

This demo application represents simple bitcoin trading platform, where users can realize generated buy and sell orders.

On the left side, there is a trade offer stack that lists the offer a user can realize. On the right side, there is a real-time USD/BTC price chart.

The price for USD/BTC is always set to the latest processed trade transaction (chart is updated every second), i.e. the users actions
have influence on the values displayed on the chart. There is at least one "AI" trader on the market, so some (random) transaction will
be realised every 3 seconds, which results in some trade offers disappearing from the trade offer stack as well as in current USD/BTC price change, which is reflected in the chart.

## How to run

### Command line

`mvn clean test-compile exec:java`

Once started, the application can be accessed from the browser at [http://localhost:8025/](http://localhost:8025) URL.

### Glassfish

Run the example as follows:

```
mvn clean package
$AS_MAIN/bin/asadmin start-domain
$AS_MAIN/bin/asadmin deploy --force ./target/*war
```

Once started, the application can be accessed from the browser at [http://localhost:8080/sample-btc-xchange](http://localhost:8080/sample-btc-xchange) URL.
