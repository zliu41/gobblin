/* (c) 2015 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */

package gobblin.source.extractor.extract;

import gobblin.source.extractor.Extractor;


/**
 * A base {@link gobblin.source.extractor.Extractor} class for
 * event-based sources.
 *
 * @author ziliu
 */
public abstract class EventBasedExtractor<S, D> implements Extractor<S, D> {

}