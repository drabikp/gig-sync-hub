/**
 * Where requests come in: this feature's REST controllers (each implementing the interface generated from its
 * OpenAPI spec), schedules and workers. They call a use case and map; nothing else.
 */
@PrimaryAdapter
package sk.drabikp.bzscraper.sync.adapter.in.worker;

import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
