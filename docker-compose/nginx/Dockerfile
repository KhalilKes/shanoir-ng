# Shanoir NG - Import, manage and share neuroimaging data
# Copyright (C) 2009-2019 Inria - https://www.inria.fr/
# Contact us on https://project.inria.fr/shanoir/
# 
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
# 
# You should have received a copy of the GNU General Public License
# along with this program. If not, see https://www.gnu.org/licenses/gpl-3.0.html

FROM ohif/viewer:v4.12.9.17613 AS viewer

FROM nginx

COPY nginx.conf http.conf https.conf shanoir.template.conf shanoir.template.dev.conf shanoir.template.prod.conf /etc/nginx/

COPY viewer/app-config.js viewer/ohif-viewer.template.conf /etc/nginx/viewer/
COPY --from=viewer /usr/share/nginx/html/. /etc/nginx/viewer/html/

COPY entrypoint entrypoint_common /bin/



COPY webapp/ /etc/nginx/html/

ENTRYPOINT ["/bin/entrypoint"]
CMD ["nginx", "-g", "daemon off;"]
