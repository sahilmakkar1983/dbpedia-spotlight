/*
 * Copyright 2012 DBpedia Spotlight Development Team
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  Check our project website for information on how to acknowledge the authors and how to contribute to the project: http://spotlight.dbpedia.org
 */

package org.dbpedia.spotlight.evaluation

import org.apache.commons.logging.LogFactory
import io.Source
import org.dbpedia.spotlight.util.AnnotationFilter
import org.dbpedia.spotlight.string.WikiLinkParser
import scala.collection.JavaConversions._
import java.io.{PrintStream, File}
import org.dbpedia.spotlight.model.{SpotlightFactory, SpotlightConfiguration, DBpediaResource, DBpediaResourceOccurrence}
import org.dbpedia.spotlight.annotate.DefaultParagraphAnnotator
import org.dbpedia.spotlight.disambiguate.{ParagraphDisambiguatorJ, TwoStepDisambiguator}

/**
 * Reads in manually annotated paragraphs, computes the inter-annotator agreement, then compares
 * our system against the union or intersection of the manual annotators.
 *
 * TODO Create client for Spotlight in the same style created for Alchemy, Ontos, OpenCalais, WikiMachine and Zemanta. i.e. using the Web Service.
 *
 * @author pablomendes
 */
object EvaluateTagExtraction
{
    private val LOG = LogFactory.getLog(this.getClass)

    val configuration = new SpotlightConfiguration("conf/eval.properties");
//    val confidence = 0.0;
//    val support = 0;
    val factory = new SpotlightFactory(configuration)
    val disambiguator = new ParagraphDisambiguatorJ(new TwoStepDisambiguator(factory))

    val spotter = factory.spotter()

    val targetTypesList = null; //TODO filter by type
    val coreferenceResolution = true;

    val annotator = new DefaultParagraphAnnotator(spotter, disambiguator);
    val filter = new AnnotationFilter(configuration)

    //val randomBaseline = new DefaultAnnotator(spotter, new RandomDisambiguator(contextSearcher))

//    val tfidfLuceneManager = new LuceneManager.CaseInsensitiveSurfaceForms(directory)
//    tfidfLuceneManager.setContextSimilarity(new DefaultSimilarity())
//    tfidfLuceneManager.setDefaultAnalyzer(analyzer);
//    val tfidfBaseline  = new DefaultAnnotator(spotter, spotSelector, new MergedOccurrencesDisambiguator(new MergedOccurrencesContextSearcher(tfidfLuceneManager)))

//    val factory = new LuceneFactory(configuration)
//    val annotator = factory.annotator()
//    val filter = factory.filter()



    def main(args : Array[String])
    {

        //      val baseDir: String = "/home/pablo/eval/manual"
        //      val inputFile: File = new File(baseDir+"AnnotationText.txt");
        //      val prefix = "spotlight/AnnotationText-Spotlight";
        //      val outputFile: File = new File(baseDir+prefix+".txt.matrix");

        //      val baseDir: String = "/home/pablo/eval/cucerzan/"
        //      val inputFile: File = new File(baseDir+"cucerzan.txt");

//                val baseDir: String = "/home/pablo/eval/manual/"
//                val inputFile: File = new File(baseDir+"AnnotationText.txt");

//        val baseDir: String = "/home/pablo/eval/wikify/"
//        val inputFile: File = new File(baseDir+"gold/WikifyAllInOne.txt");

//        val baseDir: String = "/home/pablo/eval/grounder/"
//        val inputFile: File = new File(baseDir+"gold/g1b_spotlight.txt");

//        val baseDir: String = "/home/pablo/eval/csaw/"
//        val inputFile: File = new File(baseDir+"gold/paragraphs.txt");

        val baseDir: String = "/home/pablo/eval/bbc/"
        val inputFile: File = new File(baseDir+"gold/transcripts.txt");

        val prefix = "spotlight/Spotlight";
        val setOutputFile: File = new File(baseDir+prefix+"NoFilter.set");

        if (!new File(baseDir).exists) {
            System.err.println("Base directory does not exist. "+baseDir);
            exit();
        }

        //ANNOTATE AND TRANSFORM TO MATRIX

        val plainText = Source.fromFile(inputFile).mkString // Either get from file
        //val plainText = AnnotatedTextParser.eraseMarkup(text)     //   or erase markup


        // Cleanup last run.
        for (confidence <- EvalParams.confidenceInterval) {
            for(support <- EvalParams.supportInterval) {
                new File(baseDir+prefix+".c"+confidence+"s"+support+".set").delete
            }
        }

        val allOut = new PrintStream(setOutputFile)

        val randomBaselineResults = new PrintStream(baseDir+prefix+"Random.set")

        val top10Score = new PrintStream(baseDir+prefix+"Top10Score.set");
        val top10Confusion = new PrintStream(baseDir+prefix+"Top10Confusion.set");
        val top10Prior = new PrintStream(baseDir+prefix+"Top10Prior.set");
        val top10Confidence = new PrintStream(baseDir+prefix+"Top10Confidence.set");
        val top10Context = new PrintStream(baseDir+prefix+"Top10Context.set");

        var i = 0;
        for (text <- plainText.split("\n\n")) {
            val cleanText = WikiLinkParser.eraseMarkup(text);

            //TODO run baselines
            //val baselineOcc = randomBaseline.annotate(cleanText).toList
            //randomBaselineResults.append("\n"+baselineOcc.map(o => o.resource.uri).toSet.mkString("\n")+"\n")

            i = i + 1
            var occs = List[DBpediaResourceOccurrence]()
            try {
                LOG.info("Paragraph "+i)
                occs = annotator.annotate(cleanText).toList;
            } catch {
                case e: Exception =>
                    LOG.error("Exception: "+e);
            }

            allOut.append("\n"+EvalUtils.rank(occs,_.similarityScore).map(_._1).mkString("\n")+"\n")

            val sixPercent = (cleanText.split("\\s+").size * 0.06).round.toInt;
            //val sixPercent = 20;
            val k = Math.min(occs.size, sixPercent)

            val entitiesByScore = EvalUtils.rank(occs, _.similarityScore).map(_._1);
            top10Score.append("\n"+entitiesByScore.slice(0,k).mkString("\n")+"\n")

            val entitiesByConfusion = EvalUtils.rank(occs, _.percentageOfSecondRank).reverse.map(_._1); //should not be reversed. small percentage is large gap.
            top10Confusion.append("\n"+entitiesByConfusion.slice(0,k).mkString("\n")+"\n")

            val entitiesByPrior = EvalUtils.rank(occs, _.resource.prior).map(_._1)
            top10Prior.append("\n"+entitiesByPrior.slice(0,k).mkString("\n")+"\n")

            val entitiesByConfidence =EvalUtils.rank(occs, o=> (o.similarityScore * (1-o.percentageOfSecondRank)) ).map(_._1);
            top10Confidence.append("\n"+entitiesByConfidence.slice(0,k).mkString("\n")+"\n")

            val entitiesByContext =EvalUtils.rank(occs, _.contextualScore ).map(_._1);
            top10Context.append("\n"+entitiesByContext.slice(0,k).mkString("\n")+"\n")

            EvalUtils.writeResultsForIntervals(baseDir, prefix, occs, "doc"+i, configuration.getSimilarityThresholds.map(_.doubleValue).toList)

        }
        //out.close();
        top10Score.close()
        top10Prior.close()
        top10Confusion.close()
        top10Confidence.close()
        top10Context.close()

        allOut.close();
        randomBaselineResults.close();


        SetEvaluation.run(baseDir)

    }


}