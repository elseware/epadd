package edu.standford.epadd;

import edu.stanford.epadd.RepositoryInfo;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RepositoryInfoTest {

    @Test
    public void repositoryInfoContructorTest() {
        RepositoryInfo repositoryInfo = new RepositoryInfo("repositoryName", "institutionName");
        assert StringUtils.equals(repositoryInfo.repositoryName, "repositoryName");
        assert StringUtils.equals(repositoryInfo.institutionName, "institutionName");
        assert repositoryInfo.numberOfCollections == 0;
        assert repositoryInfo.numberOfMessages == 0;

        RepositoryInfo repositoryInfo2 = new RepositoryInfo("repositoryName", "institutionName");
        assert repositoryInfo.equals(repositoryInfo2);
    }
}