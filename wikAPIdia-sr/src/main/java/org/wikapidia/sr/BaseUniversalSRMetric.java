package org.wikapidia.sr;

import edu.emory.mathcs.backport.java.util.Arrays;
import gnu.trove.set.TIntSet;
import org.wikapidia.core.dao.DaoException;
import org.wikapidia.core.dao.UniversalPageDao;
import org.wikapidia.core.lang.LocalId;
import org.wikapidia.core.lang.LocalString;
import org.wikapidia.core.model.UniversalPage;
import org.wikapidia.matrix.SparseMatrix;
import org.wikapidia.matrix.SparseMatrixRow;
import org.wikapidia.sr.disambig.Disambiguator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Logger;

public abstract class BaseUniversalSRMetric implements UniversalSRMetric{
    private static Logger LOG = Logger.getLogger(BaseUniversalSRMetric.class.getName());
    protected int numThreads = Runtime.getRuntime().availableProcessors();
    protected UniversalPageDao universalPageDao;
    protected Disambiguator disambiguator;
    protected int algorithmId;


    protected SparseMatrix mostSimilarUniversalMatrix;

    public BaseUniversalSRMetric(Disambiguator disambiguator, UniversalPageDao universalPageDao, int algorithmId){
        this.universalPageDao = universalPageDao;
        this.disambiguator = disambiguator;
        this.algorithmId = algorithmId;
    }

    public void setMostSimilarUniversalMatrix(SparseMatrix matrix) {
        this.mostSimilarUniversalMatrix = matrix;
    }

    public boolean hasCachedMostSimilarUniversal(int wpId){
        boolean hasCached;
        try {
            hasCached = mostSimilarUniversalMatrix != null && mostSimilarUniversalMatrix.getRow(wpId) != null;
        } catch (IOException e) {
            return false;
        }
        return hasCached;
    }

    public SRResultList getCachedMostSimilarUniversal(int wpId, int numResults, TIntSet validIds){
        if (!hasCachedMostSimilarUniversal(wpId)){
            return null;
        }
        SparseMatrixRow row;
        try {
            row = mostSimilarUniversalMatrix.getRow(wpId);
        } catch (IOException e) {
            return null;
        }
        int n = 0;
        SRResultList srl = new SRResultList(row.getNumCols());
        for (int i = 0;i < row.getNumCols() &&  n < numResults; i++) {
            int wpId2 = row.getColIndex(i);
            if (validIds == null || validIds.contains(wpId2)) {
                srl.set(n++, row.getColIndex(i), row.getColValue(i));
            }
        }
        srl.truncate(n);
        return srl;
    }

    @Override
    public abstract SRResult similarity(UniversalPage page1, UniversalPage page2, boolean explanations) throws DaoException;

    @Override
    public SRResult similarity(LocalString phrase1, LocalString phrase2, boolean explanations) throws DaoException {
        HashSet<LocalString> context = new HashSet<LocalString>();
        context.add(phrase2);
        LocalId similar1 = disambiguator.disambiguate(phrase1, context);
        context.clear();
        context.add(phrase1);
        LocalId similar2 = disambiguator.disambiguate(phrase2, context);
        if (similar1==null|| similar2==null){
            return new SRResult(Double.NaN);
        }
        int uId1 = universalPageDao.getUnivPageId(similar1.asLocalPage(),algorithmId);
        UniversalPage up1 = universalPageDao.getById(uId1,algorithmId);
        int uId2 = universalPageDao.getUnivPageId(similar2.asLocalPage(),algorithmId);
        UniversalPage up2 = universalPageDao.getById(uId1,algorithmId);
        return similarity(up1,up2,explanations);

    }


    @Override
    public abstract SRResultList mostSimilar(UniversalPage page, int maxResults, boolean explanations) throws DaoException;

    @Override
    public abstract SRResultList mostSimilar(UniversalPage page, int maxResults, boolean explanations, TIntSet validIds) throws DaoException;

    @Override
    public SRResultList mostSimilar(LocalString phrase, int maxResults, boolean explanations) throws DaoException {
        return mostSimilar(phrase, maxResults, explanations, null);
    }

    @Override
    public SRResultList mostSimilar(LocalString phrase, int maxResults, boolean explanations, TIntSet validIds) throws DaoException {
        LocalId localId = disambiguator.disambiguate(phrase,null);
        if (localId == null){
            SRResultList resultList = new SRResultList(1);
            resultList.set(0, new SRResult(Double.NaN));
            return resultList;
        }
        int uId = universalPageDao.getUnivPageId(localId.asLocalPage(),algorithmId);
        UniversalPage up = universalPageDao.getById(uId,algorithmId);
        return mostSimilar(up,maxResults,explanations,validIds);
    }

    @Override
    public double[][] cosimilarity(int[] rowIds, int[] colIds) throws IOException, DaoException {
        double[][] cos = new double[rowIds.length][colIds.length];
        for (int i=0; i<rowIds.length; i++){
            for (int j=0; j<colIds.length; j++){
                if (rowIds[i]==colIds[j]){
                    cos[i][j]=1;
                }
                else {
                    cos[i][j]=similarity(
                        new UniversalPage(rowIds[i], algorithmId, null, null),
                        new UniversalPage(colIds[j], algorithmId, null, null),
                        false).getValue();
                }
            }
        }
        return cos;
    }

    @Override
    public double[][] cosimilarity(LocalString[] rowPhrases, LocalString[] colPhrases) throws IOException, DaoException {
        int rowIds[] = new int[rowPhrases.length];
        int colIds[] = new int[colPhrases.length];
        List<LocalId> rowLocalIds = disambiguator.disambiguate(Arrays.asList(rowPhrases),new HashSet<LocalString>(Arrays.asList(colPhrases)));
        List<LocalId> colLocalIds = disambiguator.disambiguate(Arrays.asList(colPhrases),new HashSet<LocalString>(Arrays.asList(rowPhrases)));
        for (int i=0; i<rowIds.length; i++){
            rowIds[i] = universalPageDao.getUnivPageId(rowLocalIds.get(i).asLocalPage(),algorithmId);
        }
        for (int i=0; i<colIds.length; i++){
            colIds[i] = universalPageDao.getUnivPageId(colLocalIds.get(i).asLocalPage(),algorithmId);
        }
        return cosimilarity(rowIds, colIds);
    }

    @Override
    public double[][] cosimilarity(int[] ids) throws IOException, DaoException {
        double[][] cos = new double[ids.length][ids.length];
        for (int i=0; i<ids.length; i++){
            cos[i][i]=1;
        }
        for (int i=0; i<ids.length; i++){
            for (int j=i+1; j<ids.length; j++){
                cos[i][j]=similarity(
                        new UniversalPage(ids[i], 0, null, null),
                        new UniversalPage(ids[j], 0, null, null),
                        false).getValue();
            }
        }
        for (int i=1; i<ids.length; i++){
            for (int j=i-1; j>-1; j--){
                cos[i][j]=cos[j][i];
            }
        }
        return cos;
    }

    @Override
    public double[][] cosimilarity(LocalString[] phrases) throws IOException, DaoException {
        int ids[] = new int[phrases.length];
        List<LocalId> localIds = disambiguator.disambiguate(Arrays.asList(phrases), null);
        for (int i=0; i<phrases.length; i++){
            ids[i] = universalPageDao.getUnivPageId(localIds.get(i).asLocalPage(),algorithmId);
        }
        return cosimilarity(ids);
    }

    @Override
    public int getAlgorithmId() {
        return this.algorithmId;
    }
}
