/**   Notificaltions Logger
 *
 *    Copyright 2021 kkossev
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 *
 *    Change history:
 * 
 *    ver. 1.0.0  -  2021-06-19 - inital versiion
 *    ver. 1.0.1  -  2021-11-03 - driver name changed
*/

metadata
{
    definition(name: "Notificaltions Logger", namespace: "kkossev", author: "kkossev")
    {
        capability "Notification"
    }
}

void deviceNotification(text) {
    log.info "${text}"
}

void updated() {
    log.info "Updated..."
}

void installed() {
    log.info "Installed..."
}

void parse(String description) { log.warn "parse(String description) not implemented" }

void parse(List<Map> description) { log.warn "parse(String description) not implemented" }

