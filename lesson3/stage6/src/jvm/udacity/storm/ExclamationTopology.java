package udacity.storm;

import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.StormSubmitter;
import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.testing.TestWordSpout;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import backtype.storm.utils.Utils;

import java.util.HashMap;
import java.util.Map;

import udacity.storm.spout.MyLikesSpout;
import udacity.storm.spout.MyNamesSpout;

/**
 * This is a basic example of a storm topology.
 *
 * This topology demonstrates how to add three exclamation marks '!!!'
 * to each word emitted
 *
 * This is an example for Udacity Real Time Analytics Course - ud381
 *
 */
public class ExclamationTopology {

  /**
   * A bolt that adds the exclamation marks '!!!' to word
   */
  public static class ExclamationBolt extends BaseRichBolt
  {
    // To output tuples from this bolt to the next stage bolts, if any
    OutputCollector _collector;

    private Map<String, String> favoritesMap;


    @Override
    public void prepare(
        Map                     map,
        TopologyContext         topologyContext,
        OutputCollector         collector)
    {
      // save the output collector for emitting tuples
      _collector = collector;

      favoritesMap = new HashMap<String, String>();
    }

    @Override
    public void execute(Tuple tuple)
    {
      //**** ADD COMPONENT ID

      /*
       * Use component id to modify behavior
       */
      String componentId = tuple.getSourceComponent();

      if (componentId.equals("my-likes")) {
        String name = tuple.getString(0);
        String favorite = tuple.getString(1);

        if (favoritesMap.get(name) == null) {
          favoritesMap.put(name, favorite);
        }
      } else if (componentId.equals("my-names")) {
        String name = tuple.getString(0);
        String favorite = favoritesMap.get(name);

        if (favorite != null) {
          StringBuilder exclamatedSentence = new StringBuilder();
          exclamatedSentence.append(name).append("\'s favorite is ").append(favorite).append("!!!");

          _collector.emit(tuple, new Values(exclamatedSentence.toString()));
        }
      } else if (componentId.equals("exclaim1")) {
        String sentence = tuple.getString(0);

        StringBuilder exclamatedSentence = new StringBuilder();
        exclamatedSentence.append(sentence).append("!!!");

        _collector.emit(tuple, new Values(exclamatedSentence.toString()));
      }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer)
    {
      // tell storm the schema of the output tuple for this spout

      // tuple consists of a single column called 'exclamated-word'
      declarer.declare(new Fields("exclamated-word"));
    }
  }

  public static void main(String[] args) throws Exception
  {
    // create the topology
    TopologyBuilder builder = new TopologyBuilder();

    builder.setSpout("my-likes", new MyLikesSpout(), 10);
    builder.setSpout("my-names", new MyNamesSpout(), 10);

    // attach the exclamation bolt to the topology - parallelism of 3
    builder.setBolt("exclaim1", new ExclamationBolt(), 3)
      .shuffleGrouping("my-likes")
      .shuffleGrouping("my-names");

    // attach another exclamation bolt to the topology - parallelism of 2
    builder.setBolt("exclaim2", new ExclamationBolt(), 2).shuffleGrouping("exclaim1");

    builder.setBolt("report", new ReportBolt(), 1).globalGrouping("exclaim2");

    // create the default config object
    Config conf = new Config();

    // set the config in debugging mode
    conf.setDebug(true);

    if (args != null && args.length > 0) {

      // run it in a live cluster

      // set the number of workers for running all spout and bolt tasks
      conf.setNumWorkers(3);

      // create the topology and submit with config
      StormSubmitter.submitTopology(args[0], conf, builder.createTopology());

    } else {

      // run it in a simulated local cluster

      // create the local cluster instance
      LocalCluster cluster = new LocalCluster();

      // submit the topology to the local cluster
      cluster.submitTopology("exclamation", conf, builder.createTopology());

      // let the topology run for 30 seconds. note topologies never terminate!
      Thread.sleep(30000);

      // kill the topology
      cluster.killTopology("exclamation");

      // we are done, so shutdown the local cluster
      cluster.shutdown();
    }
  }
}
