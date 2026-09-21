package company.vk.edu.distrib.compute.masha533.urlshortener;

import company.vk.edu.distrib.compute.AbstractHttpServiceFactory;
import company.vk.edu.distrib.compute.urlshortener.UrlShortenerService;

import java.io.IOException;

public class UrlShortenerServiceFactory extends AbstractHttpServiceFactory<UrlShortenerService> {

    @Override
    protected UrlShortenerService doCreate(int port) throws IOException {
        return new UrlShortenerServiceImpl(port);
    }
}
