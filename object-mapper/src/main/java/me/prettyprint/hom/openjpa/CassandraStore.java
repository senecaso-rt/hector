package me.prettyprint.hom.openjpa;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import org.apache.openjpa.kernel.OpenJPAStateManager;
import org.apache.openjpa.meta.ClassMetaData;
import org.apache.openjpa.meta.FieldMetaData;
import org.apache.openjpa.meta.JavaTypes;
import org.apache.openjpa.util.OpenJPAId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ColumnMapRowMapper;

import me.prettyprint.cassandra.model.HColumnImpl;
import me.prettyprint.cassandra.model.MutatorImpl;
import me.prettyprint.cassandra.serializers.BytesArraySerializer;
import me.prettyprint.cassandra.serializers.LongSerializer;
import me.prettyprint.cassandra.serializers.StringSerializer;
import me.prettyprint.cassandra.utils.StringUtils;
import me.prettyprint.hector.api.Cluster;
import me.prettyprint.hector.api.Keyspace;
import me.prettyprint.hector.api.Serializer;
import me.prettyprint.hector.api.beans.ColumnSlice;
import me.prettyprint.hector.api.factory.HFactory;
import me.prettyprint.hector.api.mutation.Mutator;
import me.prettyprint.hector.api.query.QueryResult;
import me.prettyprint.hector.api.query.SliceQuery;
import me.prettyprint.hom.EntityManagerConfigurator;
import me.prettyprint.hom.openjpa.EntityFacade.ColumnMeta;


/**
 * Holds the {@link Cluster} and {@link Keyspace} references needed
 * for accessing Cassandra
 * 
 * @author zznate
 */
public class CassandraStore {

  private static final Logger log = LoggerFactory.getLogger(CassandraStore.class);
  
  private final Cluster cluster;
  private final CassandraStoreConfiguration conf;
  private Keyspace keyspace;
  private MappingUtils mappingUtils;
  
  public CassandraStore(CassandraStoreConfiguration conf) {
    this.conf = conf;
    this.cluster = HFactory.getCluster(conf.getValue(EntityManagerConfigurator.CLUSTER_NAME_PROP)
        .getOriginalValue());
    // TODO needs passthrough of other configuration
    mappingUtils = new MappingUtils();
  }
  
  public CassandraStore open() {
    this.keyspace = HFactory.createKeyspace(conf.getValue(EntityManagerConfigurator.KEYSPACE_PROP)
        .getOriginalValue(), cluster);
    return this;
  }
  
  public <I> boolean getObject(OpenJPAStateManager stateManager, I idObj) {
    // build CFMappingDef
    ClassMetaData metaData = stateManager.getMetaData();
    EntityFacade entityFacade = new EntityFacade(metaData);
    SliceQuery<byte[], String, byte[]> sliceQuery = mappingUtils.buildSliceQuery(idObj, entityFacade, keyspace);
    
    QueryResult<ColumnSlice<String, byte[]>> result = sliceQuery.execute();
    
    stateManager.storeString(1, StringUtils.string(result.get().getColumnByName("name").getValue()));
    stateManager.storeObject(0, idObj);
    
    return true;
  }
  
  public Mutator storeObject(Mutator mutator, OpenJPAStateManager stateManager, Object idObj) {
    if ( mutator == null )
      mutator = new MutatorImpl(keyspace, BytesArraySerializer.get());
    if ( log.isDebugEnabled() ) {
      log.debug("Adding mutation for class {}", stateManager.getManagedInstance().getClass().getName());
    }
    ClassMetaData metaData = stateManager.getMetaData();       
    EntityFacade entityFacade = new EntityFacade(metaData);
    for (Map.Entry<String,ColumnMeta<?>> entry : entityFacade.getColumnMeta().entrySet()) {
      mutator.addInsertion(mappingUtils.getKeyBytes(idObj), entityFacade.getColumnFamilyName(), 
          new HColumnImpl(entry.getKey(), stateManager.fetch(entry.getValue().fieldId), 
              keyspace.createClock(), StringSerializer.get(),
              entry.getValue().serializer));
          
    }    
    return mutator;
  }
  
  

  public Cluster getCluster() {
    return cluster;
  }

  public Keyspace getKeyspace() {
    return keyspace;
  }


  
  
}
