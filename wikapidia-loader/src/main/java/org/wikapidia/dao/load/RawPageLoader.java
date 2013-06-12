package org.wikapidia.dao.load;

import org.wikapidia.core.WikapidiaException;
import org.wikapidia.core.dao.DaoException;
import org.wikapidia.core.dao.LocalPageDao;
import org.wikapidia.core.dao.RawPageDao;
import org.wikapidia.core.model.LocalPage;
import org.wikapidia.core.model.RawPage;
import org.wikapidia.parser.wiki.ParserVisitor;

/**
 */
public class RawPageLoader extends ParserVisitor {
    private final RawPageDao dao;

    public RawPageLoader(RawPageDao dao) {
        this.dao = dao;
    }

    @Override
    public void beginPage(RawPage xml) throws WikapidiaException {
        try {
            dao.save(xml);
        } catch (DaoException e) {
            throw new WikapidiaException(e);
        }
    }
}