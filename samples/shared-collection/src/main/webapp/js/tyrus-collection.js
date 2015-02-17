/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

/**
 * Tyrus namespace.
 *
 * @namespace
 */
Tyrus = {};

/**
 * Tyrus.Collection namespace.
 *
 * @namespace
 */
Tyrus.Collection = {};

/**
 * Create new Tyrus map.
 *
 * @param {string} url
 * @param {function} updateListener
 * @constructor
 */
Tyrus.Collection.Map = function (url, updateListener) {
    var self = this;

    if (!(self instanceof Tyrus.Collection.Map)) {
        throw new Error("You must use 'new' to construct Tyrus collection.");
    }

    if (!url || url === null || typeof url !== "string") {
        throw new Error("Parameter 'url' must be present and must be a string.")
    }

    if (updateListener && updateListener !== null && typeof updateListener !== "function") {
        throw new Error("Parameter 'updateListener' must be a function.")
    }


    var map = {};
    var online = false;

    var _websocket = new WebSocket(url);

    // private methods

    var _onOpen = function () {
        online = true;
    };

    var _onMessage = function (event) {
        var message = JSON.parse(event.data);
        switch (message.event) {
            case "init":
                map = message.map;
                break;
            case "put":
                map[message.key] = message.value;
                break;
            case "remove":
                delete map[message.key];
                break;
            case "clear":
                map = {};
                break;
        }

        updateListener();
    };

    var _onError = function (event) {
        console.log("_onError " + event);
    };

    var _onClose = function () {
        online = false;
    };

    _websocket.onopen = _onOpen;
    _websocket.onmessage = _onMessage;
    _websocket.onerror = _onError;
    _websocket.onclose = _onClose;

    var _validateKey = function (key) {
        if (!key || key === null || typeof key !== "string") {
            throw new Error("Parameter 'key' must be present and must be a string.")
        }
    };

    var _send = function (message) {
        if (online) {
            _websocket.send(JSON.stringify(message));
        }
        updateListener();
    };

    // "privileged" methods.

    /**
     * Get size of the map.
     *
     * @returns {Number} number of records in the map.
     */
    self.size = function () {
        return Object.keys(map).length;
    };

    /**
     * Return {@code true} when the map is empty.
     *
     * @returns {boolean} {@code true} when the map is empty, {@code false} otherwise.
     */
    self.isEmpty = function () {
        return self.size() === 0;
    };

    /**
     * Get value corresponding to provided key from a map.
     *
     * @param {string} key key.
     * @returns {*} value for corresponding key or {@code null} when there is no such key.
     */
    self.get = function (key) {
        _validateKey(key);

        if (map.hasOwnProperty(key)) {
            return map[key];
        }

        return null;
    };

    /**
     * Put an item into the map.
     *
     * @param {string} key key.
     * @param {*} value value.
     */
    self.put = function (key, value) {
        _validateKey(key);

        map[key] = value;

        _send({event: "put", key: key, value: value});
    };

    /**
     * Remove key (and corresponding value) from the map.
     *
     * @param {string} key key to be removed.
     */
    self.remove = function (key) {
        _validateKey(key);

        delete map[key];

        _send({event: "remove", key: key});
    };

    /**
     * Clear the map.
     */
    self.clear = function () {
        map = {};

        _send({event: "clear"});
    };

    /**
     * Get the key set.
     *
     * @returns {Array} array containing all keys from the map (as indexes AND values - TODO).
     */
    self.keySet = function () {
        var result = [];

        for (var key in map) {
            if (map.hasOwnProperty(key)) {
                result[key] = key;
            }
        }

        return result;
    };
};

/**
 * Create new rest map.
 *
 * @param {string} restUrl
 * @param {function} updateListener
 * @constructor
 */
Tyrus.Collection.RestMap = function (restUrl, updateListener) {
    var self = this;

    if (!(self instanceof Tyrus.Collection.RestMap)) {
        throw new Error("You must use 'new' to construct Tyrus collection.");
    }

    if (!restUrl || restUrl === null || typeof restUrl !== "string") {
        throw new Error("Parameter 'postUrl' must be present and must be a string.")
    }

    if (updateListener && updateListener !== null && typeof updateListener !== "function") {
        throw new Error("Parameter 'updateListener' must be a function.")
    }

    var map = {};
    var online = false;

    var source = new EventSource(restUrl);

    // private methods

    var _onOpen = function () {
        online = true;
    };

    var _onMessage = function (event) {
        var message = JSON.parse(event.data);

        switch (message.event) {
            case "init":
                map = message.map;
                break;
            case "put":
                map[message.key] = message.value;
                break;
            case "remove":
                delete map[message.key];
                break;
            case "clear":
                map = {};
                break;
        }

        updateListener();
    };

    var _onError = function (event) {
        console.log("_onError " + event);
    };

    var _onClose = function () {
        online = false;
    };

    source.onopen = _onOpen;
    source.onerror = _onError;
    source.onclose = _onClose;
    source.addEventListener("update", _onMessage, false);

    var _validateKey = function (key) {
        if (!key || key === null || typeof key !== "string") {
            throw new Error("Parameter 'key' must be present and must be a string.")
        }
    };

    var _send = function (message) {
        var xmlhttp;
        if (window.XMLHttpRequest) {
            // code for IE7+, Firefox, Chrome, Opera, Safari
            xmlhttp = new XMLHttpRequest();
        } else {
            // code for IE6, IE5
            xmlhttp = new ActiveXObject("Microsoft.XMLHTTP");
        }
        xmlhttp.open("POST", restUrl, true);
        xmlhttp.send(JSON.stringify(message));
        updateListener();
    };

    // "privileged" methods.

    /**
     * Get size of the map.
     *
     * @returns {Number} number of records in the map.
     */
    self.size = function () {
        return Object.keys(map).length;
    };

    /**
     * Return {@code true} when the map is empty.
     *
     * @returns {boolean} {@code true} when the map is empty, {@code false} otherwise.
     */
    self.isEmpty = function () {
        return self.size() === 0;
    };

    /**
     * Get value corresponding to provided key from a map.
     *
     * @param {string} key key.
     * @returns {*} value for corresponding key or {@code null} when there is no such key.
     */
    self.get = function (key) {
        _validateKey(key);

        if (map.hasOwnProperty(key)) {
            return map[key];
        }

        return null;
    };

    /**
     * Put an item into the map.
     *
     * @param {string} key key.
     * @param {*} value value.
     */
    self.put = function (key, value) {
        _validateKey(key);

        map[key] = value;

        _send({event: "put", key: key, value: value});
    };

    /**
     * Remove key (and corresponding value) from the map.
     *
     * @param {string} key key to be removed.
     */
    self.remove = function (key) {
        _validateKey(key);

        delete map[key];

        _send({event: "remove", key: key});
    };

    /**
     * Clear the map.
     */
    self.clear = function () {
        map = {};

        _send({event: "clear"});
    };

    /**
     * Get the key set.
     *
     * @returns {Array} array containing all keys from the map (as indexes AND values - TODO).
     */
    self.keySet = function () {
        var result = [];

        for (var key in map) {
            if (map.hasOwnProperty(key)) {
                result[key] = key;
            }
        }

        return result;
    };
};
