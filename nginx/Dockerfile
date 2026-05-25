FROM nginx:alpine

# Remove the default Nginx configuration file
RUN rm /etc/nginx/conf.d/default.conf

# Copy your custom configuration file into the image
COPY nginx.conf /etc/nginx/nginx.conf

# Expose port 80 inside the container
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]