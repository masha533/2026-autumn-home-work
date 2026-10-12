package company.vk.edu.distrib.compute.masha533.kv;

import company.vk.edu.distrib.compute.Dao;
import company.vk.edu.distrib.compute.kv.ClusterDaoFactoryTest;
import company.vk.edu.distrib.compute.kv.RemoteDaoFactory;
import java.io.IOException;

@ClusterDaoFactoryTest
public class ClusterRemoteDaoFactoryImpl implements RemoteDaoFactory<String> {
    @Override
    public Dao<String> create(int... ports) throws IOException {
        return new ClusterRemoteDao(ports);
    }
}
